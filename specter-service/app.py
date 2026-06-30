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

@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok'})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001)
