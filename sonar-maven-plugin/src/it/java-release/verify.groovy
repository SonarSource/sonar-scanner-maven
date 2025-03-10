Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def target = 'sonar.java.target'
def source = 'sonar.java.source'
assert properties."$target" == '8'
assert properties."$source" == '8'
