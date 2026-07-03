from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer
import torch

app = Flask(__name__)
model = SentenceTransformer('allenai/specter2_base')

@app.route('/embed', methods=['POST'])
def embed():
    data = request.get_json()
    text = data.get('text', '')
    if not text or not text.strip():
        return jsonify({'error': 'empty text'}), 400
    embedding = model.encode(text, convert_to_numpy=True)
    return jsonify({'embedding': embedding.tolist()})

@app.route('/ocr', methods=['POST'])
def ocr():
    """OCR fallback for scanned PDFs (UniSubmit Phase 7).

    Contract: JSON {filename, data(base64)} -> {text}.
    Requires optional deps: pytesseract + pdf2image (+ tesseract binary);
    answers 501 when they are not installed so the Java side degrades cleanly.
    """
    try:
        import base64, tempfile, os
        import pytesseract
        from pdf2image import convert_from_path
        from PIL import Image
    except ImportError as e:
        return jsonify({'error': f'OCR dependencies not installed: {e}'}), 501

    payload = request.get_json()
    filename = (payload or {}).get('filename', 'document')
    data = (payload or {}).get('data', '')
    if not data:
        return jsonify({'error': 'no file data'}), 400

    raw = base64.b64decode(data)
    suffix = os.path.splitext(filename)[1] or '.pdf'
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(raw)
        path = tmp.name
    try:
        texts = []
        if suffix.lower() == '.pdf':
            for page in convert_from_path(path, dpi=200):
                texts.append(pytesseract.image_to_string(page))
        else:
            texts.append(pytesseract.image_to_string(Image.open(path)))
        return jsonify({'text': '
'.join(texts)})
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    finally:
        os.unlink(path)


@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok'})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001)
