Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def sonarJavaSource = 'sonar.java.source'
def moduleOverrideRelease = "org.sonarsource:override-release.$sonarJavaSource"
def moduleInheritRelease = "org.sonarsource:inherit-release.$sonarJavaSource"

assert properties."$moduleOverrideRelease" == "21"
assert properties."$moduleInheritRelease" == "17"
