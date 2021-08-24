pipeline {
    agent any
    options {
        retry(count: 3, delay: fixed(time: 10, unit: 'SECONDS'))
    }
    stages {
        stage('x') {
            steps {
                echo 'Trying!'
                error('oops')
            }
        }
    }
    post {
        always {
            echo 'Done!'
        }
    }
}