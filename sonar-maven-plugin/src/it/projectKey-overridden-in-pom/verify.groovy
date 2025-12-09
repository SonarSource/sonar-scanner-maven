Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def projectKey = 'sonar.projectKey'
def modules = 'sonar.modules'

// We test that the project key is the one in the pom properties of the project
assert properties.'sonar.projectKey' == 'this-property-overrides-the-project-key'
// We test that we have one submodule detected by the scanner
assert properties.'sonar.modules' == 'org.example:module1'
// We test that the submodule does not have a sonar.projectKey property
assert !properties.hasProperty('org.example:module1.sonar.projectKey')