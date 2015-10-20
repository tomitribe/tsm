define(
    [
        'jquery', 'can', 'model/repository',
        'text!../../partial/repositories.mustache', 'text!../../partial/repository.mustache'
    ],
    function ($, can, Repository, PluralView, SingularView) {

    can.view.mustache('repositories', PluralView);
    can.view.mustache('repository', SingularView);

    var showRepository = function (repository) {
        can.view(
            'repository',
            {
                title: repository.id ? 'Update ' + repository.name : 'Create a new repository',
                repository: repository,
                doSubmit: function () {
                    repository.save(
                        function () {
                            location.hash = '#!repositories';
                        },
                        function (xhr, ignored, error) {
                            alert('HTTP ' + xhr.status + ', ' + error);
                        }
                    );
                },
                doDelete: function () {
                    repository.destroy(
                        function () {
                            location.hash = '#!repositories';
                        },
                        function (xhr, ignored, error) {
                            alert('HTTP ' + xhr.status + ', ' + error);
                        }
                    );
                }
            },
            function (html) { $('#content').html(html); });
    };

    return {
        all: function () {
            Repository.findAll()
                .then(function (repositories) {
                    can.view('repositories', { repositories: repositories }, function (html) { $('#content').html(html); });
                });
        },
        single: function (params) {
            if (params.id) {
                Repository.findOne(params)
                    .then(function (repository) {
                        showRepository(repository);
                    });
            } else {
                showRepository(new Repository({name: '', base: ''}));
            }
        }
    };
});
