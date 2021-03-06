def configure_foreman_environment() {
    try {
        databaseFile(gemset())
    } catch(all) {

        updateGitlabCommitStatus state: 'failed'
        cleanup(get_ruby_version(branch_map))
        throw(all)

    }
}

def get_ruby_version(branches) {
  target_branch = env.getProperty('gitlabTargetBranch')
  return branches[target_branch]['ruby']
}

def setup_foreman(ruby = '2.2') {
    try {

        configureRVM(ruby)

        withRVM(['bundle install --jobs=5 --retry=2 --without mysql:mysql2'], ruby)

        // Create DB first in development as migrate behaviour can change
        withRVM(['bundle exec rake db:drop -q || true'], ruby)
        withRVM(['bundle exec rake db:create -q'], ruby)
        withRVM(['bundle exec rake db:migrate -q'], ruby)
        withRVM(['bundle exec rake db:drop -q RAILS_ENV=test || true'], ruby)
        withRVM(['bundle exec rake db:create -q RAILS_ENV=test'], ruby)
        withRVM(['bundle exec rake db:migrate -q RAILS_ENV=test'], ruby)

        if (fileExists('package.json')) {
              sh 'npm install --save npm'
              sh 'npm install phantomjs'
              withRVM(['./node_modules/.bin/npm install'], ruby)
        }

    } catch (all) {

        updateGitlabCommitStatus state: 'failed'
        cleanup(get_ruby_version(branch_map))
        throw(all)

    }
}

def setup_plugin(plugin_name) {
        // Ensure we don't mention the gem twice in the Gemfile in case it's already mentioned there
        sh "find Gemfile bundler.d -type f -exec sed -i \"/gem ['\\\"]${plugin_name}['\\\"]/d\" {} \\;"
        // Now let's introduce the plugin
        sh "echo \"gemspec :path => '\$(pwd)/../plugin', :name => '${plugin_name}', :development_group => '${plugin_name}_dev'\" >> bundler.d/Gemfile.local.rb"
        // Plugin specifics..
        if(fileExists("../plugin/gemfile.d/${plugin_name}.rb")) {
            sh "cat ../plugin/gemfile.d/${plugin_name}.rb >> bundler.d/Gemfile.local.rb"
        }
}
