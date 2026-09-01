def log = new File(basedir, 'build.log').text

assert log.contains('BUILD SUCCESS')
assert log.contains('No forbidden packages found')
assert new File(basedir, 'target/clean-bundle-1.0.0.jar').isFile()
