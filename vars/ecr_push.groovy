// ecr_push.groovy — ECR login + docker push via STS assume-role
//
// Why assume-role instead of using the instance role directly:
//   Instance role only needs sts:AssumeRole (tiny blast radius).
//   CartOps-Jenkins-CI-Role holds the actual ECR permissions.
//   Temp creds last 1 hr.
//
// Why env vars instead of named AWS profiles:
//   aws configure set reads + rewrites the ENTIRE ~/.aws/credentials file.
//   Five parallel stages doing this simultaneously corrupt the file mid-write
//   (classic read-modify-write race). Env vars are process-local — no shared
//   file, no race, no cleanup step needed.

def call(String repoName, String imageTag, String region, String ecrRegistry) {

    def roleArn = env.ECR_ROLE_ARN   // set in pipeline environment block

    sh """
        set -e

        # ── 1. STS assume-role → get short-lived credentials ──────────────────
        ASSUMED=\$(aws sts assume-role \\
            --role-arn ${roleArn} \\
            --role-session-name cartops-ecr-${env.BUILD_NUMBER} \\
            --output json)

        KEY=\$(echo "\$ASSUMED"    | python3 -c 'import json,sys; print(json.load(sys.stdin)["Credentials"]["AccessKeyId"])')
        SECRET=\$(echo "\$ASSUMED" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Credentials"]["SecretAccessKey"])')
        TOKEN=\$(echo "\$ASSUMED"  | python3 -c 'import json,sys; print(json.load(sys.stdin)["Credentials"]["SessionToken"])')

        # ── 2. ECR login — creds passed as env vars, never written to disk ────
        AWS_ACCESS_KEY_ID="\$KEY" \\
        AWS_SECRET_ACCESS_KEY="\$SECRET" \\
        AWS_SESSION_TOKEN="\$TOKEN" \\
        aws ecr get-login-password --region ${region} | \\
            docker login --username AWS --password-stdin ${ecrRegistry}

        # ── 3. Push image ──────────────────────────────────────────────────────
        docker push ${ecrRegistry}/${repoName}:${imageTag}
    """
}
