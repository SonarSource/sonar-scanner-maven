Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
  properties.load(it)
}

def mavenVersion = properties.'maven.version'
def isMaven4 = mavenVersion.startsWith('4.')

assert properties.'sonar.password' == '123abc'

assert properties.'sonar.scanner.irrelevantSecretFromEnv' == '{AES}cannot-decrypt'
assert properties.'sonar.irrelevant.secret.from.pom' == '{AES}cannot-decrypt'
assert properties.'sonar.irrelevant.secret.from.cli' == '{AES}cannot-decrypt'

if (isMaven4) {
  // FIXME SCANMAVEN-341 Maven 4 decrypts all properties early
  assert properties.'another.password' == '123abc'
  assert properties.'another.password.with.equals' == '123abc'
  assert properties.'another.password.with.comment' == '123abc'
} else {
  assert !properties.stringPropertyNames().contains('another.password')
  // FIXME uncomment this to reproduce SCANMAVEN-341 issues
  // assert !properties.stringPropertyNames().contains('another.password.with.equals')
  // assert !properties.stringPropertyNames().contains('another.password.with.comment')
}

