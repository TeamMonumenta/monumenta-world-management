" Remove whitespace from end of lines when saving
autocmd BufWritePre * :%s/\s\+$//e
autocmd Filetype xml setlocal expandtab
