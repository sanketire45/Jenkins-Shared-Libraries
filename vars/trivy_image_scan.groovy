// trivy_image_scan.groovy — scans a built Docker image for known CVEs
//
// Called AFTER docker build, BEFORE ECR push.
// Fails the pipeline on any CRITICAL or HIGH vulnerability so nothing
// broken reaches the registry.

def call(String imageName) {
    // Each parallel scan gets its own --cache-dir to avoid fanal-cache lock contention.
    // The vulnerability DB is copied from the default cache (downloaded in trivy_scan stage)
    // so --skip-db-update works without re-downloading.
    // Cache dir is always deleted in finally so /tmp doesn't fill up across builds.
    def safeImageName = imageName.replaceAll('[^A-Za-z0-9]', '_')
    def cacheDir = "/tmp/trivy-${safeImageName}"
    try {
        sh """
            mkdir -p ${cacheDir}
            cp -r \$HOME/.cache/trivy/db ${cacheDir}/ 2>/dev/null || true
            trivy image --exit-code 1 --severity CRITICAL,HIGH --no-progress \\
                --skip-db-update --cache-dir ${cacheDir} ${imageName}
        """
    } finally {
        sh "rm -rf ${cacheDir} || true"
    }
}
