Properties properties = new Properties()
new File(basedir, 'out.properties').withInputStream {
    properties.load(it)
}

def aSourcepath = properties['sourcepath:A.sonar.java.sourcepath']
def bSourcepath = properties['sourcepath:B.sonar.java.sourcepath']
def cSourcepath = properties['sourcepath:C.sonar.java.sourcepath']
def dSourcepath = properties['sourcepath:D.sonar.java.sourcepath']
assert aSourcepath == 'src/main/java,target/generated-sources/annotations,../B/src/main/java,../B/target/generated-sources/annotations,../C/src/main/java,../C/target/generated-sources/annotations'
assert bSourcepath == 'src/main/java,target/generated-sources/annotations,../C/src/main/java,../C/target/generated-sources/annotations,../D/src/main/java,../D/target/generated-sources/annotations'
assert cSourcepath == 'src/main/java,target/generated-sources/annotations'
assert dSourcepath == 'src/main/java,target/generated-sources/annotations'
assert !properties.containsKey('sonar.java.sourcepath')
