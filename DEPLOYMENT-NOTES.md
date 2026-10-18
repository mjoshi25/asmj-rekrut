# ASMJ Recruitment deployment notes

## Google Meet / Microsoft Teams invitations

1. Create the meeting in Google Meet or Microsoft Teams outside ASMJ Recruitment.
2. Copy the meeting's HTTPS invitation URL.
3. Open **Interviews & meetings**, select one or more candidates, choose the job and provider, paste the link, set the schedule and create the invitation.
4. The application saves a portal notification for each selected candidate and sends email only when SMTP is configured and working.

The app does not automatically create meetings inside Google or Microsoft; that would require separate Google/Microsoft OAuth and calendar API setup.

## Cloudinary storage for offer letters and joining documents

New offer letters and candidate joining documents are uploaded to Cloudinary as authenticated raw assets under `asmj-recruitment/candidates/...`. The application stores the Cloudinary public ID and metadata in the existing record store, and authorized downloads are routed through the application before a signed Cloudinary delivery URL is generated. New uploads do not require a Render persistent disk or `ASMJ_UPLOAD_DIR`.

Configure these Render environment variables using the same Cloudinary account already used for candidate profile photos:

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`

All three must be populated with values from the Cloudinary dashboard. If they are missing or incorrect, document/offer uploads will show a configuration or Cloudinary API error rather than silently writing to a local folder. `ASMJ_UPLOAD_DIR` is retained only for backward-compatible downloads of older files that still exist on local storage. Existing documents that were saved only on a previous temporary Render filesystem cannot be migrated automatically unless those files still exist.

Files are limited to 8 MB. Offer letters accept PDF, DOC or DOCX; joining documents accept PDF, PNG, JPG/JPEG, DOC or DOCX.

## Diagnosing a persistent offer-letter 400 response

After deploying this diagnostic build, submit the offer letter once and open Render **Logs** for the same service and timestamp. The application writes one of these markers:

- `OFFER_UPLOAD_REQUEST_RECEIVED`: the POST reached the application. Check the following exception or final HTTP status.
- `OFFER_UPLOAD_REQUEST_EXCEPTION`: multipart parsing or request handling threw an exception; the stack trace should identify it.
- `OFFER_UPLOAD_REQUEST_FINISHED ... status=400`: the request reached the app but returned 400 without an uncaught exception; check the form/CSRF and application logs around that timestamp.

If none of these markers appears, verify that the ZIP's contents were committed to the GitHub repository connected to the correct Render service and that a new deploy completed. These markers are emitted before Spring MVC parses the multipart request, so the offer controller itself does not need to run for the first marker to appear.


## Cloudinary storage for offer letters and joining documents

New offer letters and candidate joining documents are uploaded to Cloudinary as authenticated raw assets under `asmj-recruitment/candidates/...`. The application stores the Cloudinary public ID and metadata in the existing record store, and authorized downloads are routed through the application before a signed Cloudinary delivery URL is generated. The local `ASMJ_UPLOAD_DIR` is no longer required for new uploads; it is retained only for backward-compatible downloads of older files already stored on disk.

Configure these Render environment variables (the same Cloudinary account already used for candidate profile photos):

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`

All three must be populated with values from the Cloudinary dashboard. If they are missing or incorrect, document/offer uploads will show a configuration or Cloudinary API error rather than silently writing to a local folder. Existing documents that were saved only on a previous temporary Render filesystem cannot be migrated automatically unless those files still exist.
