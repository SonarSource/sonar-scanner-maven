Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def target = 'sonar.java.target'
def source = 'sonar.java.source'

// Should prefer default-compile

assert properties."$target" == '9'
assert properties."$source" == '9'


def logs = new File(basedir, 'build.log').readLines()
assert containsCompilerWarning(logs)

static boolean containsCompilerWarning(List<String> logs) {
    for (String log: logs) {
        if (log.startsWith("[WARNING]") &&
                log.endsWith("Heterogeneous compiler configuration has been detected. Using compiler configuration from execution: 'default-compile'")) {
            return true
        }
    }
    return false
}

