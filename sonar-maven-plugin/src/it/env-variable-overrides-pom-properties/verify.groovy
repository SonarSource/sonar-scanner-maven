// See SCANMAVEN-129
Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def sonarHostUrl = 'sonar.host.url'
assert properties."$sonarHostUrl" == "http://from-env.org:9000"
