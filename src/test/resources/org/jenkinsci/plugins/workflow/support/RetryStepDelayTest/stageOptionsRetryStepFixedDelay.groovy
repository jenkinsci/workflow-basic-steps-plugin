pipeline {
    agent any
    stages {
        stage('x') {
            options {
                retry(count: 3, delay: fixed(time: 10, unit: 'SECONDS'))
            }
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