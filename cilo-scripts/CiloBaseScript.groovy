import org.codehaus.groovy.control.CompilerConfiguration
import java.util.regex.*;

abstract class CiloBaseScript extends Script {
    abstract def runCode()

    private static def dockerEnv = System.getenv()
    private static def BUILD_NUMBER

    private static Boolean firstRun = true;
    private static def steps = [:]
    private static def secretsMap = [:]
    private static def envMap = [:]
    
    private static def isInsideSshClosure = false
    private static def sshBoundAddress
    private static def sshBoundIdentityFile

    private static def stdOutInterceptor
    private static def stdErrInterceptor
    
    // used by macro processor
    protected static def stdMap = [:]
    protected static def stdOut = ""
    protected static def stdErr = ""
    protected static def exitCode = 0
    
    def run() {
        BUILD_NUMBER = dockerEnv["BUILD_NUMBER"].toInteger()
        if (firstRun) {
            firstRun = false
        } else {
            return
        }
        // Secret Interception
        def interceptorClosure = { secretsMap, str ->
            def newString = str
            for (pair in secretsMap) {
                def key = pair.key;
                def value = pair.value.trim();
                def valueArray = value.split('\n')
                for (valueLine in valueArray) {
                    def trimmed = valueLine.trim()
                    newString = newString.replace(trimmed, "********")
                }
            }
            return newString;
        }
        stdOutInterceptor = new SecretInterceptor(secretsMap, interceptorClosure, true)
        stdErrInterceptor = new SecretInterceptor(secretsMap, interceptorClosure, false)
        stdOutInterceptor.start()
        stdErrInterceptor.start()
        
        beforePipeline()
        try {
            final result = runCode()
            for (step in steps) {
                def stepString = "${step.key}"
                def lineCount = 80-stepString.length()
                def line = "-".multiply(lineCount)
                println "-".multiply(80)
                println "-".multiply(76)+"STEP"
                println "${line}${stepString}"
                println "-".multiply(80)
                beforeEachStep()
                step.value() // run closure
                afterEachStep()
            }
        } catch (e) {
            e.printStackTrace()
        } finally {
            afterPipeline()
        }
    }

    def beforeEachStep() {

    }

    def afterEachStep() {

    }
    
    def beforePipeline() {
        
    }
    
    def afterPipeline() {
        
    }

    public static def step(name, closure) {
        closure.delegate = this;
        steps[name] = closure
    }
                    
    public static def ciloShellScript(filename) {
        shell("chmod 700 $filename")
        if (isInsideSshClosure) {
            shell("touch ${filename}.ssh", false)
            shell("chmod 700 ${filename}.ssh", false)
            def sshFile = new File("${filename}.ssh")
            sshFile << "#!/usr/bin/env sh\n"
            sshFile << "ssh -o \"StrictHostKeyChecking no\" -i ${sshBoundIdentityFile} ${sshBoundAddress} 'bash -s' < ${filename}\n"
            // TODO: pass envMap through ssh.
            //  StackOverflow: https://stackoverflow.com/questions/4409951/can-i-forward-env-variables-over-ssh
            println "Attempting to run ssh script at \"${sshBoundAddress}\" using identity \"${sshBoundIdentityFile}\""
            def shReturn = shell("${filename}.ssh")
            sshFile.delete()
            return shReturn
        } else {
            return shell("${filename}")
        }
    }

    public static def ssh(sshAddressString, identityFile, closure) {
        def prevSsh = isInsideSshClosure
        def prevBoundAddress = sshBoundAddress
        def prevBoundIdentityFile = sshBoundIdentityFile
        isInsideSshClosure = true
        sshBoundAddress = sshAddressString
        sshBoundIdentityFile = identityFile
        closure()
        isInsideSshClosure = prevSsh
        sshBoundAddress = prevBoundAddress
        sshBoundIdentityFile = prevBoundIdentityFile
    }
    
    public static def shell(command, shouldPrint = true) {
        StringBuilder stdOut = new StringBuilder()
        StringBuilder stdErr = new StringBuilder()
        int exitCode = 1
        def environment=[]
        System.getenv().each{ k, v -> environment<<"$k=$v" }
        secretsMap.each{ k, v -> environment<<"$k=$v" }
        envMap.each{ k, v -> environment<<"$k=$v" }
        def proc = command.execute(environment, new File("/home/cilo/workspace/"))
        proc.in.eachLine { line ->
            def newString=line
            for (pair in secretsMap) {
                def key = pair.key;
                def value = pair.value.trim();
                def valueArray = value.split('\n')
                for (valueLine in valueArray) {
                    def trimmed = valueLine.trim()
                    newString = newString.replace(trimmed, "********")
                }
            }
            stdOut.append(line)
            println newString;
        }
        def minutes = 60
        proc.waitForOrKill(minutes*60*1000)
        stdErr.append(proc.err.text)
        exitCode = proc.exitValue()
        return ["stdOut":stdOut, "stdErr":stdErr, "exitCode":exitCode]
    }

    public static def env(map, closure) {
        for (pair in map) {
            def key=pair.key
            def value=pair.value
            envMap << ["${key}":"${value}"]
        }
        closure.delegate = this;
        closure.call()
        for (pair in map) {
            def key=pair.key
            def value=pair.value
            envMap.remove("${key}")
        }
    }

    public static def secrets(namesMap, closure) {
        def binding = new Binding()
        for (name in namesMap) {
            shell("cilo-decrypt-secret ${name}")
            def nameText = "${name}Text"
            def nameBytes = "${name}Bytes"
            def nameFile = "${name}File"
            def secretFile = new File("/home/cilo/secret/${name}")
            if (secretFile == null || secretFile.length() <= 0) {
                throw new IllegalArgumentException("Secret '${name}' is either empty or does not exist.")
            }
            def secretBytes = secretFile.getBytes()
            def secretText = secretFile.getText()
            secretsMap << ["${name}":"${secretText}"]
            secretsMap << ["${nameText}":"${secretText}"]
            secretsMap << ["${nameBytes}":"${secretBytes}"]
            secretsMap << ["${nameFile}":"/home/cilo/secret/${name}"]
            for (secretPair in secretsMap) {
                binding.setVariable(secretPair.key, secretPair.value)
            }
        }
        closure.delegate = this;
        closure.setBinding(binding)
        closure.call()
        for (name in namesMap) {
            def nameText = "${name}Text"
            def nameBytes = "${name}Bytes"
            def nameFile = "${name}File"
            def secretFile = new File("/home/cilo/secret/${name}")
            if (secretFile == null || secretFile.length() <= 0) {
                throw new IllegalArgumentException("Secret '${name}' is either empty or does not exist.")
            }
            def secretBytes = secretFile.getBytes()
            def secretText = secretFile.getText()
            secretsMap.remove("${name}")
            secretsMap.remove("${nameText}")
            secretsMap.remove("${nameBytes}")
            secretsMap.remove("${nameFile}")
            shell("rm /home/cilo/secret/${name}")
        }
    }
    
    public static def secret(name, closure) {
        secrets([name], closure)
    }
}

class SecretInterceptor extends java.io.FilterOutputStream {
    public  Closure callback;
    public boolean output;
    public LinkedHashMap secretsMap
    public PrintStream outStream
    SecretInterceptor(LinkedHashMap secretsMap, final Closure callback, Boolean output) {
        super(output ? System.out : System.err);
        assert secretsMap != null;
        this.secretsMap = secretsMap;
        assert callback != null;
        this.callback = callback;
        this.output = true;
    }
    public void start() {
        outStream = new PrintStream(this)
        if (output) {
            System.setOut(outStream);
        } else {
            System.setErr(outStream);
        }
    }
    public void stop() {
        if (output) {
            System.setOut(System.out);
        } else {
            System.setErr(System.err);
        }
    }
    public void write(byte[] b) throws IOException {
        String newString = (String) callback.call(secretsMap, new String(b));
        def newBytes = newString.getBytes();
        out.write(newBytes);
    }
    public void write(byte[] b, int off, int len) throws IOException {
        String newString = (String) callback.call(secretsMap, new String(b, off, len));
        def newBytes = newString.getBytes();
        out.write(newBytes, 0, newBytes.length);
    }
    public void write(int b) throws IOException {
        String newString = (String) callback.call(secretsMap, String.valueOf((char) b));
        out.write(b);
    }
}
