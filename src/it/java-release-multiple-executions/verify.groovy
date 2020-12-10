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
assert logs.contains('[WARNING] Heterogeneous compiler configuration has been detected. Using compiler configuration from execution: \'default-compile\'')
