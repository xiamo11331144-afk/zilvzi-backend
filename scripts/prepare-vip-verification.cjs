// Prepare an isolated Maven project; never write backend/target or run database tests here.
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const root = path.resolve(__dirname, '..')
const mini = path.join(root, 'miniprogram')
let jsCount = 0
let jsonCount = 0
function check(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith('.') || entry.name === 'node_modules') continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) check(full)
    else if (entry.name.endsWith('.js')) { new vm.Script(fs.readFileSync(full, 'utf8'), { filename: full }); jsCount++ }
    else if (entry.name.endsWith('.json')) { JSON.parse(fs.readFileSync(full, 'utf8')); jsonCount++ }
  }
}
check(mini)
const app = JSON.parse(fs.readFileSync(path.join(mini, 'app.json'), 'utf8'))
for (const page of app.pages) {
  for (const extension of ['.js', '.json', '.wxml', '.wxss']) {
    if (!fs.existsSync(path.join(mini, page + extension))) throw new Error(`Missing registered page: ${page + extension}`)
  }
}
console.log(`Mini-program syntax: ${jsCount} JS, ${jsonCount} JSON; registered pages: ${app.pages.length}`)
const output = path.join(root, '.verification', 'vip-backend')
fs.mkdirSync(output, { recursive: true })
const xmlPath = value => value.replaceAll('\\', '/').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
const backend = path.join(root, 'backend')
let pom = fs.readFileSync(path.join(backend, 'pom.xml'), 'utf8')
pom = pom.replace('<build>', `<build>
        <directory>${xmlPath(path.join(output, 'build'))}</directory>
        <sourceDirectory>${xmlPath(path.join(backend, 'src/main/java'))}</sourceDirectory>
        <testSourceDirectory>${xmlPath(path.join(backend, 'src/test/java'))}</testSourceDirectory>
        <resources><resource><directory>${xmlPath(path.join(backend, 'src/main/resources'))}</directory></resource></resources>
        <testResources><testResource><directory>${xmlPath(path.join(backend, 'src/test/resources'))}</directory></testResource></testResources>`)
fs.writeFileSync(path.join(output, 'pom.xml'), pom)
console.log('Prepared .verification/vip-backend/pom.xml; run Maven with MENTOR_DB_TESTS=false and Java 17.')
