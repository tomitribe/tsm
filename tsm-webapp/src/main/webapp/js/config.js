require.config({
    baseUrl: 'js',
    paths: {
        'jquery': '../webjars/jquery/2.1.4/jquery.min',
        'jquery-ui': '../webjars/jquery-ui/1.11.4/jquery-ui.min',
        'text': '../webjars/requirejs-text/2.0.10-3/text',
        'bootstrap': '../webjars/bootstrap/3.3.4/js/bootstrap.min',
        'can': 'can.min'
    },
    shim: {
        'bootstrap': [ 'jquery' ]
    }
});
require([ 'app' ]);
