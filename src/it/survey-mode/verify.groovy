def log = new File(basedir, 'build.log').text

assert log.contains('BUILD SUCCESS')
assert log.contains('[WARNING]')
assert log.contains('javax.servlet')
assert !log.contains('BUILD FAILURE')
