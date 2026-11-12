# OnePage 0.4.2 validation

## Release workflow

The initial tag build failed before publication because no certificate digest
was extracted from the runner SDK report. Selecting the newest SDK executable
made verification depend on the runner image. The workflow installs and uses
Build Tools 36.0.0 explicitly, matching the locally verified tool.
The signature check accepts both signer label formats and requires every
reported signer to match the published certificate. APK verification errors
remain fatal, and an unrecognized report is printed for diagnosis.

The extracted workflow step was exercised with the four signed release APKs:

| Report | Expected and observed |
| --- | --- |
| Legacy signer label | Accepted |
| SDK-range signer label | Accepted |
| Repeated identical certificate | Accepted |
| Wrong certificate | Rejected |
| Empty certificate report | Rejected |
| APK verifier failure | Rejected |
| Two different certificates | Rejected |

The original parser also rejects the SDK-range label in a regression fixture.
The updated YAML parses successfully and all eight shell steps pass `bash -n`.
Manual dispatch checks out the existing version tag; published tags and files
are preserved.

## Installation documentation

Both README files were rendered with GitHub's Markdown API. Downloader code
`9954974` resolves to the stable universal APK URL. Both installation cards
retain their logo images and Obtainium import link in the rendered HTML.
