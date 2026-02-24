# Publishing ucotron/sdk to Packagist

## Prerequisites

- A [Packagist](https://packagist.org) account
- GitHub repository with push access
- Composer installed locally (`composer validate --strict` must pass)

## Step 1: Register Package on Packagist

1. Go to <https://packagist.org/packages/submit>
2. Enter the GitHub repository URL: `https://github.com/ucotron-ai/ucotron`
3. Packagist will auto-detect `memory_arena/sdks/php/composer.json`
   - If the monorepo root is detected instead, use a subtree split or configure the package path
4. Click **Submit**

> **Note**: Packagist reads version information from git tags. The `composer.json` does not include a `version` field — this is intentional per Packagist best practices.

## Step 2: Configure GitHub Webhook for Auto-Updates

### Option A: Automatic (Recommended)

Packagist sets up a GitHub webhook automatically when you submit a package. Verify it exists:

1. Go to **GitHub** → Repository **Settings** → **Webhooks**
2. Confirm a webhook to `https://packagist.org/api/github` exists
3. Events: **push** events only

### Option B: Manual API Token

If the automatic webhook doesn't work, configure manual updates:

1. Go to **Packagist** → **Profile** → **API Token**
2. Copy the API token
3. Add GitHub repository secrets:
   - `PACKAGIST_USERNAME`: Your Packagist username
   - `PACKAGIST_TOKEN`: Your Packagist API token
4. The `release.yml` workflow will notify Packagist on every release via:
   ```bash
   curl -s -X POST "https://packagist.org/api/update-package?username=$USERNAME&apiToken=$TOKEN" \
     -d '{"repository":{"url":"https://github.com/ucotron-ai/ucotron"}}' \
     -H "Content-Type: application/json"
   ```

## Step 3: Publish Version 0.1.0

1. Create and push a git tag:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
2. The `release.yml` GitHub Actions workflow will:
   - Sync the version into `composer.json` from the tag
   - Run `composer validate --strict`
   - Run `vendor/bin/phpunit`
   - Notify Packagist (if secrets are configured)
3. Packagist will pick up the new tag within minutes

## Verification

After publishing, verify the package is available:

```bash
composer require ucotron/sdk:0.1.0
```

Or check the Packagist page: <https://packagist.org/packages/ucotron/sdk>

## CI/CD Integration

| Workflow | Job | Purpose |
|----------|-----|---------|
| `ci.yml` | `php-sdk` | Tests on PHP 8.1, 8.2, 8.3 matrix |
| `release.yml` | `publish-php` | Version sync + validation + Packagist notify |
| `release-dry-run.yml` | `publish-php-dry-run` | Dry run validation (no publish) |

## Version Management

Versions are managed via `scripts/sync_versions.sh`:
```bash
./scripts/sync_versions.sh 0.2.0
```

This updates `composer.json` (and all other SDK version files) to the specified version.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `composer validate` fails | Check JSON syntax, ensure no `version` field in source |
| Packagist doesn't update | Verify webhook in GitHub settings, or add `PACKAGIST_TOKEN` secret |
| Tests fail in CI | Check PHP version matrix compatibility (requires ^8.1) |
| Lock file warning | Run `composer update --lock` locally and commit |
