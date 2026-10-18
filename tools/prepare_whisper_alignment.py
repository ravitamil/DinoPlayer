"""Expose Whisper cross-attention already present in the shipped quantized graph.

Uses the official tiny.en alignment heads and the fourth decoder-output contract
from sherpa-onnx v1.13.8's export-onnx-with-attention.py. Weights and logits are unchanged.
Requires onnx; writes a separate model and leaves the source intact.
"""
import argparse
from pathlib import Path
import onnx
from onnx import TensorProto, helper

HEADS = {1: [0], 2: [0, 5], 3: [0, 1, 2, 3, 4]}

def prepare(source: Path, destination: Path):
    model = onnx.load(str(source))
    if len(model.graph.output) != 3:
        raise ValueError('Expected the original three-output Whisper decoder')
    if model.graph.output[0].type.tensor_type.shape.dim[-1].dim_value != 51864:
        raise ValueError('Expected the English tiny.en vocabulary')
    softmax = [node for node in model.graph.node if node.op_type == 'Softmax']
    if len(softmax) != 8:
        raise ValueError('Expected alternating self/cross attention for four decoder layers')
    names = []
    for layer, heads in HEADS.items():
        indices = 'caption_alignment_heads_' + str(layer)
        output = 'caption_alignment_layer_' + str(layer)
        model.graph.initializer.append(helper.make_tensor(indices, TensorProto.INT64, [len(heads)], heads))
        model.graph.node.append(helper.make_node('Gather', [softmax[layer * 2 + 1].output[0], indices], [output], axis=1))
        names.append(output)
    model.graph.node.append(helper.make_node('Concat', names, ['cross_attention_weights'], axis=1))
    model.graph.output.append(helper.make_tensor_value_info('cross_attention_weights', TensorProto.FLOAT,
                                                           ['batch', 8, 'tokens', 'audio_frames']))
    metadata = model.metadata_props.add()
    metadata.key, metadata.value = 'caption_alignment', 'tiny.en:1:0,2:0,2:5,3:0,3:1,3:2,3:3,3:4'
    onnx.checker.check_model(model)
    onnx.save(model, str(destination))
    print('Prepared alignment decoder:', destination)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    prepare(args.source, args.destination)
