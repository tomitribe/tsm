define([
    'controller/home',
    'controller/repository'
], function (
        Home,
        Repositories) {
    return {
        'repositories route': Repositories.all,
        'repository/:id route': Repositories.single,
        'repository route': Repositories.single,
        'route': Home
    };
});
