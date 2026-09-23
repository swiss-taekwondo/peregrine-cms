var fs = require('fs');

if(!fs.existsSync('target/classes/etc/graphiql')) {
    fs.mkdirSync('target/classes/etc/graphiql')
}
fs.copyFileSync('node_modules/graphiql/graphiql.js', 'target/classes/etc/graphiql/graphiql.js')

fs.copyFileSync('node_modules/graphiql/graphiql.css', 'target/classes/etc/graphiql/graphiql.css')

if(!fs.existsSync('target/classes/etc/graphiql/cjs')) {
    fs.mkdirSync('target/classes/etc/graphiql/cjs')
}
if(!fs.existsSync('target/classes/etc/graphiql/umd')) {
    fs.mkdirSync('target/classes/etc/graphiql/umd')
}
fs.copyFileSync('node_modules/react/cjs/react.production.min.js', 'target/classes/etc/graphiql/cjs/react.js')
fs.copyFileSync('node_modules/react/umd/react.production.min.js', 'target/classes/etc/graphiql/umd/react.js')

fs.copyFileSync('node_modules/react-dom/cjs/react-dom.production.min.js', 'target/classes/etc/graphiql/cjs/react-dom.js')
fs.copyFileSync('node_modules/react-dom/umd/react-dom.production.min.js', 'target/classes/etc/graphiql/umd/react-dom.js')
