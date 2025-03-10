Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def jdkHome = 'sonar.java.jdkHome'
assert properties."$jdkHome" == 'fake_jdk_1.6'
