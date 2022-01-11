pipeline {
    agent any
    stages {
        stage('x') {
            options {
                retry(count: 4, delay: random(max: 15, min: 5, unit: 'SECONDS'))
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