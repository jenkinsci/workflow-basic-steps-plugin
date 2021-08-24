pipeline {
    agent any
    stages {
        stage('x') {
            steps {
                retry(count: 3, delay: fixed(time: 10, unit: 'SECONDS')) {
                    echo 'Trying!'
                    error('oops')
                }
            }
        }
    }
    post {
        always {
            echo 'Done!'
        }
    }
}