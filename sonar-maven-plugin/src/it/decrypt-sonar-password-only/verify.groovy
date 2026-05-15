Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
  properties.load(it)
}

assert properties.'sonar.password' == '123abc'

assert properties.'sonar.scanner.relevantSecretFromEnv' == '{AES}cannot-decrypt'
assert properties.'sonar.relevant.secret.from.pom' == '{AES}cannot-decrypt'
assert properties.'sonar.relevant.secret.from.cli' == '{AES}cannot-decrypt'
assert !properties.stringPropertyNames().contains('irrelevant.secret.from.pom')

// check for submodules' properties
def groupId = 'org.example'
def sonarModule = "$groupId:sonar-module"
def sonarModuleSonarProp = sonarModule + ".sonar.prop"
def sonarModuleUnknownProp = sonarModule + ".unknown.prop"
def anotherModule = "$groupId:another-module"
def anotherModuleSonarProp = anotherModule + ".sonar.prop"
def anotherModuleUnknownProp = anotherModule + ".unknown.prop"
assert properties."$sonarModuleSonarProp" == '{AES}cannot-decrypt'
assert !properties.stringPropertyNames().contains("$sonarModuleUnknownProp")
assert properties."$anotherModuleSonarProp" == '{AES}cannot-decrypt'
assert !properties.stringPropertyNames().contains("$anotherModuleUnknownProp")

assert !properties.stringPropertyNames().contains('another.password')
assert !properties.stringPropertyNames().contains('another.password.with.equals')
assert !properties.stringPropertyNames().contains('another.password.with.comment')

