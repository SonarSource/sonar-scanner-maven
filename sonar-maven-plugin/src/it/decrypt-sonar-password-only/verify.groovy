Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}
// SCANMAVEN-141
assert properties.'sonar.password' == '123abc'
// SCANMAVEN-228
assert properties.'sonar.scanner.irrelevantSecretFromEnv' == '{AES}cannot-decrypt'
assert properties.'sonar.irrelevant.secret.from.pom' == '{AES}cannot-decrypt'
assert properties.'sonar.irrelevant.secret.from.cli' == '{AES}cannot-decrypt'
