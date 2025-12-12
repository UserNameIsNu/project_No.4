# check_gpu.py
# import torch
# import sys
#
# print("=== 系统环境诊断 ===")
# print(f"Python版本: {sys.version}")
# print(f"PyTorch版本: {torch.__version__}")
# print(f"CUDA是否可用: {torch.cuda.is_available()}")
#
# if torch.cuda.is_available():
#     print(f"CUDA版本: {torch.version.cuda}")
#     print(f"GPU数量: {torch.cuda.device_count()}")
#     for i in range(torch.cuda.device_count()):
#         print(f"GPU {i}: {torch.cuda.get_device_name(i)}")
#         print(f"  内存: {torch.cuda.get_device_properties(i).total_memory / 1024 ** 3:.1f} GB")
# else:
#     print("❌ 未检测到CUDA设备")
#
# print(f"\n当前设备: {torch.device('cuda' if torch.cuda.is_available() else 'cpu')}")


# check_version.py
# import torch
# print(f"PyTorch版本: {torch.__version__}")
# print(f"CUDA可用: {torch.cuda.is_available()}")





import torch

print("\n=== PyTorch CUDA兼容性检查 ===")

# 检查PyTorch版本对应的CUDA版本
pytorch_version = torch.__version__
print(f"当前PyTorch版本: {pytorch_version}")

# 常见PyTorch版本对应的CUDA要求
cuda_requirements = {
    "2.0": "11.8",
    "1.13": "11.7",
    "1.12": "11.3, 11.6",
    "1.11": "11.3",
    "1.10": "11.3",
}

# 检查torch是否有cuda版本信息
if hasattr(torch.version, 'cuda'):
    print(f"PyTorch编译时的CUDA版本: {torch.version.cuda}")
else:
    print("⚠️ PyTorch可能是CPU版本")

# 检查torch.cuda模块是否可用
print(f"torch.cuda.is_available(): {torch.cuda.is_available()}")
print(f"torch.backends.cudnn.enabled: {torch.backends.cudnn.enabled}")