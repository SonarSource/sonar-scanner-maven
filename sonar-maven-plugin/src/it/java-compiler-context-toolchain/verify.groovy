Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def projectBaseDir = 'sonar.projectBaseDir'
def sep = File.separator
def jdkHome = 'sonar.java.jdkHome'
assert properties."$jdkHome" == properties."$projectBaseDir" + "${sep}fake_jdk_1.5"
