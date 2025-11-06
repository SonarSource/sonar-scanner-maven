// See SCANMAVEN-129
Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def sonarHostUrl = 'sonar.host.url'
def token = 'sonar.token'
assert properties."$sonarHostUrl" == "http://my.sonar:9000"
assert properties."$token" == "my_token"
