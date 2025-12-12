from transformers import AutoTokenizer, AutoModelForCausalLM, TextStreamer
import torch

model_path = "C:\\Users\\李进峰\\Downloads\\deepseek-aideepseek-llm-7b-base"

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
    """与模型对话（流式输出）"""
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

        # 创建流式输出器
        streamer = TextStreamer(
            tokenizer,
            skip_prompt=True,  # 跳过提示词部分，只输出新生成的内容
            skip_special_tokens=True
        )

        print("🤖 AI: ", end="", flush=True)  # 不换行，立即刷新输出

        # 生成回复（流式）
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
                eos_token_id=tokenizer.eos_token_id,
                streamer=streamer  # 关键：添加流式输出器
            )

        print()  # 流式输出结束后换行


def chat_with_model_manual():
    """手动实现流式输出的替代方案（更灵活）"""
    print("\n" + "=" * 50)
    print("开始对话 - 手动流式输出 (输入 'quit' 或 '退出' 结束)")
    print("=" * 50)

    while True:
        user_input = input("\n👤 你: ").strip()
        if user_input.lower() in ['quit', 'exit', '退出']:
            break

        # 构建提示词
        prompt = f"用户: {user_input}\n助手:"
        inputs = tokenizer(prompt, return_tensors="pt").to(model.device)

        print("🤖 AI: ", end="", flush=True)

        # 手动实现流式生成
        generated_tokens = []
        with torch.no_grad():
            # 使用 generate 但逐步获取结果
            for output in model.generate(
                    **inputs,
                    max_new_tokens=200,
                    do_sample=True,
                    temperature=0.7,
                    top_p=0.9,
                    top_k=50,
                    repetition_penalty=1.1,
                    pad_token_id=tokenizer.eos_token_id,
                    eos_token_id=tokenizer.eos_token_id,
                    return_dict_in_generate=True,
                    output_scores=True
            ):
                # 获取最新生成的token
                new_token = output.sequences[0, -1:]
                generated_tokens.append(new_token.item())

                # 解码并打印
                new_text = tokenizer.decode(new_token, skip_special_tokens=True)
                print(new_text, end="", flush=True)

                # 检查是否结束
                if new_token.item() == tokenizer.eos_token_id:
                    break

        print()  # 结束换行


# 先做一个快速测试（流式）
print("\n🧪 快速测试（流式输出）...")
test_prompt = "你好，请介绍一下人工智能"
inputs = tokenizer(test_prompt, return_tensors="pt").to(model.device)

# 创建测试用的流式输出器
test_streamer = TextStreamer(tokenizer, skip_prompt=True, skip_special_tokens=True)

print("测试回复: ", end="", flush=True)
with torch.no_grad():
    test_outputs = model.generate(
        **inputs,
        max_new_tokens=100,
        do_sample=True,
        temperature=0.7,
        streamer=test_streamer
    )

print()  # 测试结束换行

# 选择流式输出方式
print("\n请选择流式输出方式:")
print("1. 自动流式输出（推荐）")
print("2. 手动流式输出（更灵活）")
choice = input("请输入选择 (1 或 2): ").strip()

if choice == "2":
    chat_with_model_manual()
else:
    chat_with_model()

# 清理GPU内存
if torch.cuda.is_available():
    torch.cuda.empty_cache()
    print("\n🧹 已清理GPU内存")