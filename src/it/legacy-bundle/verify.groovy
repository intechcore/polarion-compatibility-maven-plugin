def log = new File(basedir, 'build.log').text

assert log.contains('BUILD FAILURE')
assert log.contains('Forbidden packages found in legacy-bundle-1.0.0.jar')
assert log.contains('javax.servlet')
assert log.contains('jakarta.servlet')
assert log.contains('com.example.Legacy')
assert log.contains('<excludedJars>')
