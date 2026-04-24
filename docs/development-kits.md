# Development Kits

# Typescript

## TypeScript Language Server

https://github.com/typescript-language-server/typescript-language-server

Can be globally installed via npm -

    npm install -g typescript-language-server

Or run directly with npx -

    npx --yes typescript-language-server --stdio

# Python 

## Python LSP Server

https://github.com/python-lsp/python-lsp-server


Recommended install in your virtual environment (you should be using one, unless you know what you're doing) -

    pip install python-lsp-server

Alternatively, you can install it globally via Homebrew (macOS) -

    brew install python-lsp-server

or your package manager of choice - 

### Arch Linux

    pacman -S python-lsp-server

### Debian/Ubuntu

    apt install python-lsp-server

Alternatives LSP Servers include -

- https://github.com/zubanls/zuban

# SASS/SCSS

## SASS Language Server

https://github.com/somesass/some-sass-language-server

Global installation via npm -

    npm install --global some-sass-language-server

To start the language server with stdio -

    some-sass-language-server --stdio

## Log Level Configuration

You can tweak the log level using the `--loglevel` argument:

    some-sass-language-server --stdio --loglevel debug

Available log levels are:
- `silent`
- `fatal`
- `error`
- `warn`
- `info` (default)
- `debug`
- `trace`
