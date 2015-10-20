define([ 'jquery', 'can', 'text!../../partial/home.mustache' ], function ($, can, View) {
    can.view.mustache('home', View);
    return function () {
        can.view('home', {}, function (html) { $('#content').html(html); });
    };
});
