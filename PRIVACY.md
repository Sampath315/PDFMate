# Privacy

## Android app

PDFMate uses Android's document picker to access files the user selects. In the current implementation, PDF processing and temporary working copies are handled on the device. The project does not define a server account, analytics service, or PDF upload backend.

Recent-document entries are stored locally by the app and refer to document URIs; the app does not intentionally copy every opened source document into a permanent library. Export, share, and print actions are initiated by the user. When a user chooses another app to receive a file, that app's privacy practices apply.

Temporary PDF data is used for processing and export. Do not treat the app's cache as durable storage; users should save important output to a location they control.

## Optional Google Colab notebook

The notebook in `colab/remove_pdf_password.ipynb` uploads the selected PDF to a Google Colab runtime for processing. This is separate from the Android app and requires an explicit user upload. The notebook does not mount Google Drive. Runtime files should be removed with its cleanup cell or will become unavailable when the runtime is terminated. Google's own terms, privacy policy, and runtime behavior apply.

## Contact

For privacy or data-handling questions, open a GitHub discussion or issue without including private documents, personal data, or passwords. Security reports should follow [SECURITY.md](SECURITY.md) and must not be posted publicly.
