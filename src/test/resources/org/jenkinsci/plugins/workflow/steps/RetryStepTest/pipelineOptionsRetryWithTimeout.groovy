pipeline {
    agent any
    options {
        retry(count: 3, timeDelay: 10, unit: 'SECONDS', useTimeDelay: true)
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