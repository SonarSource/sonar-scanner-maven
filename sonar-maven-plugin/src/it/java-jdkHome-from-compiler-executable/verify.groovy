Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def jdkHome = 'sonar.java.jdkHome'
assert new File(properties."$jdkHome") == new File(basedir, 'myjdk')
