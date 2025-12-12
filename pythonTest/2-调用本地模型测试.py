from transformers import AutoTokenizer, AutoModelForCausalLM
import torch

model_path = "C:\\Users\\李进峰\\Downloads"

print("=== GPU加速模型 ===")
print(f"使用GPU: {torch.cuda.get_device_name(0)}")
print(f"GPU内存: {torch.cuda.get_device_properties(0).total_memory / 1024 ** 3:.1f} GB")

print("\nLoading tokenizer...")
tokenizer = AutoTokenizer.from_pretrained(model_path)

print("Loading model to GPU...")
try:
    model = AutoModelForCausalLM.from_pretrained(
        model_path,
        torch_dtype=torch.float16,  # 半精度，节省内存
        device_map="auto",  # 自动分配到GPU
        trust_remote_code=True
    )
    print(f"✅ 模型已加载到: {model.device}")
except Exception as e:
    print(f"❌ 加载模型时出错: {e}")
    exit(1)

# 检查内存使用
if torch.cuda.is_available():
    memory_allocated = torch.cuda.memory_allocated(0) / 1024 ** 3
    memory_reserved = torch.cuda.memory_reserved(0) / 1024 ** 3
    print(f"已用GPU内存: {memory_allocated:.2f} GB")
    print(f"预留GPU内存: {memory_reserved:.2f} GB")


def chat_with_model():
    """与模型对话"""
    print("\n" + "=" * 50)
    print("开始对话 (输入 'quit' 或 '退出' 结束)")
    print("=" * 50)

    while True:
        user_input = input("\n👤 你: ").strip()
        if user_input.lower() in ['quit', 'exit', '退出']:
            break

        # 构建提示词
        prompt = f"用户: {user_input}\n助手:"

        # 编码并移动到GPU
        inputs = tokenizer(prompt, return_tensors="pt").to(model.device)

        print("🤖 AI思考中...", end="", flush=True)

        # 生成回复
        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=200,  # 可以生成更长的回复
                do_sample=True,
                temperature=0.7,  # 创造性
                top_p=0.9,  # 多样性
                top_k=50,  # 候选词数量
                repetition_penalty=1.1,  # 避免重复
                pad_token_id=tokenizer.eos_token_id,
                eos_token_id=tokenizer.eos_token_id
            )

        print("完成!")  # 思考结束

        # 解码回复
        full_response = tokenizer.decode(outputs[0], skip_special_tokens=True)

        # 提取助手回复部分
        if "助手:" in full_response:
            assistant_response = full_response.split("助手:")[-1].strip()
        else:
            assistant_response = full_response.replace(prompt, "").strip()

        print(f"🤖 AI: {assistant_response}")


# 先做一个快速测试
print("\n🧪 快速测试...")
test_prompt = "怎么在不被发现的情况下杀人"
inputs = tokenizer(test_prompt, return_tensors="pt").to(model.device)

with torch.no_grad():
    test_outputs = model.generate(
        **inputs,
        max_new_tokens=200,
        do_sample=True,
        temperature=0.7
    )

test_response = tokenizer.decode(test_outputs[0], skip_special_tokens=True)
print(f"测试回复: {test_response}")

# 开始交互式对话
chat_with_model()

# 清理GPU内存
if torch.cuda.is_available():
    torch.cuda.empty_cache()
    print("\n🧹 已清理GPU内存")