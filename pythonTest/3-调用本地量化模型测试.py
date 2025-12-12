from transformers import AutoTokenizer, AutoModelForCausalLM
import torch

model_path = "C:\\Users\\李进峰\\Downloads\\deepseek-aideepseek-llm-7b-base"

print("=== GPU加速模型 ===")
print(f"使用GPU: {torch.cuda.get_device_name(0)}")
print(f"GPU内存: {torch.cuda.get_device_properties(0).total_memory / 1024 ** 3:.1f} GB")

print("\nLoading tokenizer...")
tokenizer = AutoTokenizer.from_pretrained(model_path)

print("Loading model to GPU with 8-bit quantization...")
try:
    # 方法1：直接使用 load_in_8bit 参数
    model = AutoModelForCausalLM.from_pretrained(
        model_path,
        torch_dtype=torch.float16,
        device_map="auto",
        trust_remote_code=True,
        load_in_8bit=True  # 直接添加这个参数
    )
    print(f"✅ 8位量化模型已加载到: {model.device}")
except Exception as e:
    print(f"❌ 8位量化失败: {e}")

    # 方法2：如果8位失败，尝试4位
    try:
        print("尝试4位量化...")
        model = AutoModelForCausalLM.from_pretrained(
            model_path,
            torch_dtype=torch.float16,
            device_map="auto",
            trust_remote_code=True,
            load_in_4bit=True  # 使用4位量化
        )
        print(f"✅ 4位量化模型已加载到: {model.device}")
    except Exception as e2:
        print(f"❌ 4位量化也失败: {e2}")

        # 方法3：回退到无量化
        print("回退到无量化版本...")
        model = AutoModelForCausalLM.from_pretrained(
            model_path,
            torch_dtype=torch.float16,
            device_map="auto",
            trust_remote_code=True
        )
        print(f"✅ 无量化模型已加载到: {model.device}")

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
                max_new_tokens=200,
                do_sample=True,
                temperature=0.7,
                top_p=0.9,
                top_k=50,
                repetition_penalty=1.1,
                pad_token_id=tokenizer.eos_token_id,
                eos_token_id=tokenizer.eos_token_id
            )

        print("完成!")

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
test_prompt = "你好，请介绍一下人工智能"
inputs = tokenizer(test_prompt, return_tensors="pt").to(model.device)

with torch.no_grad():
    test_outputs = model.generate(
        **inputs,
        max_new_tokens=100,
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