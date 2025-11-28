def configurations = [
  [ platform: "linux", jdk: "21" ],
  [ platform: "windows", jdk: "25" ]
]

def params = [
    failFast: false,
    pit: [skip: false],
    configurations: configurations,
    checkstyle: [qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]],
    pmd: [qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]],
    spotbugs: [qualityGates: [[threshold: 1, type: 'NEW', unstable: true]]],
    jacoco: [sourceCodeRetention: 'MODIFIED', sourceDirectories: [[path: 'plugin/src/main/java']]]
]

buildPlugin(params)
