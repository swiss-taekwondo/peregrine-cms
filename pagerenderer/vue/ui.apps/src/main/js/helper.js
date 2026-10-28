
const helpers = {
    isEmpty: function(field) {
        if(field === undefined || field === null || field === '' || field === '<p><br></p>' || field.length === 0 || field === false) {
            return true
        }
        return false
    },
    areAllEmpty() {
        for(let i = 0; i < arguments.length; i++) {
            const ret = helpers.isEmpty(arguments[i])
            if(ret === false) return false
        }
        return true
    },
    pathToUrl(path) {
        if(!path || path.length < 1) return path
        if( path[0] === '#') return path
        let absoluteUrl = new RegExp('^(?:[a-z]+:)?//', 'i');   //Matches absolute URLs
        let file = new RegExp(/\.\w+$/, 'i')                    //Matches URL ending with a file extension
        if( absoluteUrl.test(path) === false ) {
            // Add .html to the path, keeping any query or hash.
            const suffixIndex = path.search(/[?#]/)
            const pathname = suffixIndex < 0 ? path : path.slice(0, suffixIndex)
            const suffix = suffixIndex < 0 ? '' : path.slice(suffixIndex)
            return file.test(pathname) ? path : `${pathname}.html${suffix}`
        }
        else return path
    }
}

const helper = {
    install(vue) {
        vue.prototype.$helper = helpers
    }
}

export default helper