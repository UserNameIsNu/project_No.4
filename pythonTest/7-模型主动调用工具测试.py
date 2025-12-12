import ollama
import time
import json
from ollama import chat


class OllamaChatClient:
    def __init__(self, model="qwen3:1.7b"):
        self.model = model
        self.conversation_history = []
        self.available_functions = self._define_functions()

        # 上下文管理配置
        self.context_config = {
            'max_history_length': 20,
            'max_tokens': 4000,
            'enable_context': True,
        }

        # 流式输出配置
        self.stream_config = {
            'char_delay': 0.08,
            'sentence_delay': 0.3,
            'comma_delay': 0.15,
        }

        self._check_service()

    def set_stream_speed(self, speed='normal'):
        """设置流式输出速度"""
        speeds = {
            'slow': {'char_delay': 0.12, 'sentence_delay': 0.5, 'comma_delay': 0.2},
            'normal': {'char_delay': 0.08, 'sentence_delay': 0.3, 'comma_delay': 0.15},
            'fast': {'char_delay': 0.03, 'sentence_delay': 0.1, 'comma_delay': 0.05}
        }
        if speed in speeds:
            self.stream_config = speeds[speed]
            print(f"✅ 已设置输出速度为: {speed}")

    def _check_service(self):
        """检查Ollama服务状态"""
        try:
            # 简单测试服务是否响应
            response = ollama.list()
            print("✅ Ollama服务连接正常")

            # 更健壮的模型列表显示
            model_names = []

            # 处理不同的响应格式
            if hasattr(response, 'models'):
                # 如果是对象属性
                for model in response.models:
                    if hasattr(model, 'name'):
                        model_names.append(model.name)
                    elif hasattr(model, 'model'):
                        model_names.append(model.model)
            elif isinstance(response, dict) and 'models' in response:
                # 如果是字典格式
                for model in response['models']:
                    if 'name' in model:
                        model_names.append(model['name'])
                    elif 'model' in model:
                        model_names.append(model['model'])
            else:
                # 尝试直接访问
                try:
                    models = getattr(response, 'models', [])
                    for model in models:
                        name = getattr(model, 'name', getattr(model, 'model', '未知'))
                        model_names.append(name)
                except:
                    model_names = ["无法解析模型列表"]

            if model_names:
                print(f"📚 可用模型: {model_names}")
            else:
                print("📚 未找到可用模型")

            return True
        except Exception as e:
            print(f"❌ 无法连接到Ollama服务: {e}")
            return False

    def _define_functions(self):
        """定义可用的工具函数"""

        def get_current_time():
            """获取当前系统时间"""
            current_time = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
            return f"当前时间是: {time.localtime()}"

        def calculate_math(expression: str):
            """计算数学表达式

            Args:
                expression: 数学表达式，如 2+2, 3*5, 10/2
            """
            try:
                # 安全计算
                allowed_chars = set('0123456789+-*/.() ')
                if all(c in allowed_chars for c in expression):
                    result = eval(expression)
                    return f"计算结果: {expression} = {result}"
                else:
                    return "错误: 表达式包含不安全字符"
            except Exception as e:
                return f"计算错误: {e}"

        def get_weather(city: str):
            """获取天气信息

            Args:
                city: 城市名称
            """
            weather_data = {
                "北京": "晴朗，25°C，微风",
                "上海": "多云，23°C，东南风",
                "广州": "阵雨，28°C，湿度80%",
                "深圳": "晴朗，27°C，微风",
                "杭州": "多云，24°C，东风",
            }
            return f"{city}的天气: {weather_data.get(city, '未知城市')}"

        return {
            'get_current_time': get_current_time,
            'calculate_math': calculate_math,
            'get_weather': get_weather,
        }

    def manage_context(self, new_message):
        """智能管理上下文，防止超过限制"""
        if not self.context_config['enable_context']:
            return [{"role": "user", "content": new_message}]

        # 添加新消息到历史
        self.conversation_history.append({"role": "user", "content": new_message})

        # 如果历史太长，进行截断
        if len(self.conversation_history) > self.context_config['max_history_length']:
            keep_messages = 6
            if len(self.conversation_history) > keep_messages:
                if len(self.conversation_history) > keep_messages + 2:
                    self.conversation_history = (
                            self.conversation_history[:2] +
                            self.conversation_history[-keep_messages:]
                    )
                else:
                    self.conversation_history = self.conversation_history[-keep_messages:]
            print("🔄 上下文已截断，保留最近对话")

        return self.conversation_history.copy()

    def add_system_message(self, system_prompt):
        """添加系统消息到对话开头"""
        system_message = {"role": "system", "content": system_prompt}
        if self.conversation_history and self.conversation_history[0].get("role") == "system":
            self.conversation_history[0] = system_message
        else:
            self.conversation_history.insert(0, system_message)

    def improved_stream_chat(self, message):
        """使用官方API的多轮工具调用方法"""
        try:
            # 管理上下文
            current_messages = self.manage_context(message)

            print("🤖 AI: ", end="", flush=True)

            # 多轮工具调用循环
            max_iterations = 5
            final_response = ""

            for iteration in range(max_iterations):
                if iteration > 0:
                    print(f"\n🔄 第 {iteration + 1} 轮工具调用...")
                    print("🤖 AI: ", end="", flush=True)

                # 使用官方chat API
                response = chat(
                    model=self.model,
                    messages=current_messages,
                    tools=[
                        {
                            "type": "function",
                            "function": {
                                "name": name,
                                "description": func.__doc__ or "",
                                "parameters": self._get_function_parameters(func)
                            }
                        } for name, func in self.available_functions.items()
                    ],
                    stream=True,
                )

                # 处理流式响应
                tool_calls_detected = False
                tool_calls = []
                full_response = ""

                for chunk in response:
                    if hasattr(chunk, 'message') and chunk.message:
                        # 检查工具调用
                        if hasattr(chunk.message, 'tool_calls') and chunk.message.tool_calls:
                            tool_calls_detected = True
                            tool_calls = chunk.message.tool_calls

                        # 处理文本内容
                        if hasattr(chunk.message, 'content') and chunk.message.content:
                            content = chunk.message.content
                            self._simulate_stream_output(content)
                            full_response += content

                # 如果没有工具调用，结束循环
                if not tool_calls_detected or not tool_calls:
                    print()  # 换行
                    final_response = full_response
                    # 将最终回复添加到历史
                    self.conversation_history.append({"role": "assistant", "content": final_response})
                    break

                # 处理工具调用
                print("\n🛠️ 检测到工具调用，执行中...")

                # 将助手消息添加到历史
                assistant_message = {
                    "role": "assistant",
                    "content": full_response,
                    "tool_calls": [
                        {
                            "function": {
                                "name": tool_call.function.name,
                                "arguments": tool_call.function.arguments
                            }
                        } for tool_call in tool_calls
                    ]
                }
                current_messages.append(assistant_message)
                self.conversation_history.append(assistant_message)

                # 执行所有工具调用
                for tool_call in tool_calls:
                    tool_name = tool_call.function.name
                    tool_args = tool_call.function.arguments

                    print(f"🔧 调用工具: {tool_name}, 参数: {tool_args}")

                    # 执行工具
                    if tool_name in self.available_functions:
                        try:
                            # 解析参数
                            if isinstance(tool_args, str):
                                parsed_args = json.loads(tool_args)
                            else:
                                parsed_args = tool_args

                            # 调用函数
                            if tool_name == 'get_current_time':
                                tool_result = self.available_functions[tool_name]()
                            else:
                                tool_result = self.available_functions[tool_name](**parsed_args)

                            print(f"📊 工具结果: {tool_result}")

                            # 将工具结果添加到消息历史
                            tool_message = {
                                "role": "tool",
                                "content": str(tool_result)
                            }
                            current_messages.append(tool_message)
                            self.conversation_history.append(tool_message)

                        except Exception as e:
                            error_msg = f"工具执行错误: {e}"
                            print(f"❌ {error_msg}")
                            tool_message = {
                                "role": "tool",
                                "content": error_msg
                            }
                            current_messages.append(tool_message)
                            self.conversation_history.append(tool_message)
                    else:
                        error_msg = f"未知工具: {tool_name}"
                        print(f"❌ {error_msg}")
                        tool_message = {
                            "role": "tool",
                            "content": error_msg
                        }
                        current_messages.append(tool_message)
                        self.conversation_history.append(tool_message)

            return final_response

        except Exception as e:
            error_msg = f"❌ 请求失败: {e}"
            print(error_msg)
            return error_msg

    def _get_function_parameters(self, func):
        """获取函数的参数信息"""
        # 简化的参数定义，实际使用时可以根据函数签名动态生成
        if func.__name__ == 'get_current_time':
            return {"type": "object", "properties": {}}
        elif func.__name__ == 'calculate_math':
            return {
                "type": "object",
                "properties": {
                    "expression": {"type": "string", "description": "数学表达式"}
                },
                "required": ["expression"]
            }
        elif func.__name__ == 'get_weather':
            return {
                "type": "object",
                "properties": {
                    "city": {"type": "string", "description": "城市名称"}
                },
                "required": ["city"]
            }
        return {"type": "object", "properties": {}}

    def _simulate_stream_output(self, text):
        """模拟流式输出（增加延迟）"""
        for char in text:
            print(char, end="", flush=True)
            if char in '。！？.!?':
                time.sleep(self.stream_config['sentence_delay'])
            elif char in '，,；;':
                time.sleep(self.stream_config['comma_delay'])
            else:
                time.sleep(self.stream_config['char_delay'])

    def clear_context(self, keep_system_message=True):
        """清空对话上下文"""
        if keep_system_message and self.conversation_history:
            system_messages = [msg for msg in self.conversation_history if msg.get("role") == "system"]
            self.conversation_history = system_messages
            print("🗑️ 已清空对话上下文（保留系统消息）")
        else:
            self.conversation_history = []
            print("🗑️ 已清空所有对话上下文")

    def show_context_info(self):
        """显示上下文信息"""
        total_messages = len(self.conversation_history)
        user_messages = len([msg for msg in self.conversation_history if msg.get("role") == "user"])
        assistant_messages = len([msg for msg in self.conversation_history if msg.get("role") == "assistant"])
        system_messages = len([msg for msg in self.conversation_history if msg.get("role") == "system"])
        tool_messages = len([msg for msg in self.conversation_history if msg.get("role") == "tool"])

        print(f"\n📊 上下文信息:")
        print(f"   总消息数: {total_messages}")
        print(f"   用户消息: {user_messages}")
        print(f"   助手回复: {assistant_messages}")
        print(f"   系统消息: {system_messages}")
        print(f"   工具调用: {tool_messages}")
        print(f"   最大限制: {self.context_config['max_history_length']} 轮对话")

    def export_context(self):
        """导出对话上下文"""
        return {
            "model": self.model,
            "conversation_history": self.conversation_history.copy(),
            "export_time": time.strftime("%Y-%m-%d %H:%M:%S")
        }

    def import_context(self, context_data):
        """导入对话上下文"""
        if "conversation_history" in context_data:
            self.conversation_history = context_data["conversation_history"]
            print("✅ 对话上下文已导入")
            self.show_context_info()
        else:
            print("❌ 无效的上下文数据")

    def test_tool_capabilities(self):
        """测试工具调用能力"""
        print("\n🧪 测试工具调用...")

        test_cases = [
            "现在几点了？",
            "计算一下 15 * 8 等于多少",
            "北京的天气怎么样？",
        ]

        for i, test_case in enumerate(test_cases, 1):
            print(f"\n{i}. 测试: '{test_case}'")
            self.improved_stream_chat(test_case)
            time.sleep(1)


def main():
    # 初始化客户端
    client = OllamaChatClient()

    print("\n=== Ollama聊天客户端 (带工具调用) ===")

    # 添加系统提示
    system_prompt = """你是一个有用的AI助手。请用中文回答用户的问题，保持友好和专业的语气。
你可以使用以下工具来帮助用户：
- get_current_time: 获取当前系统时间
- calculate_math: 计算数学表达式
- get_weather: 获取城市天气信息

当用户询问时间、计算或天气时，请使用相应的工具。
可以使用多个方法以达成用户需求。"""
    client.add_system_message(system_prompt)

    # 设置输出速度
    speed = input("设置输出速度 (slow/normal/fast, 默认normal): ").strip().lower()
    if speed in ['slow', 'normal', 'fast']:
        client.set_stream_speed(speed)
    else:
        client.set_stream_speed('normal')

    print("🔧 可用工具:")
    for tool_name in client.available_functions:
        docstring = client.available_functions[tool_name].__doc__ or ""
        description = docstring.split('Args:')[0].strip() if 'Args:' in docstring else docstring.strip()
        print(f"  - {tool_name}: {description}")

    # 测试工具能力
    client.test_tool_capabilities()

    print("\n📝 可用命令:")
    print("  - 'clear': 清空对话历史")
    print("  - 'context': 显示上下文信息")
    print("  - 'export': 导出对话上下文")
    print("  - 'system <消息>': 设置系统提示")
    print("  - 'quit/exit/退出': 退出程序")
    print("  - 'switch': 切换模型")
    print("  - 'speed': 调整输出速度")
    print("  - 'test': 重新测试工具能力")

    # 显示初始上下文信息
    client.show_context_info()

    while True:
        user_input = input("\n👤 你: ").strip()

        if user_input.lower() in ['quit', 'exit', '退出']:
            break
        elif user_input.lower() == 'clear':
            keep_system = input("是否保留系统消息？(y/n, 默认y): ").strip().lower() != 'n'
            client.clear_context(keep_system_message=keep_system)
            continue
        elif user_input.lower() == 'context':
            client.show_context_info()
            continue
        elif user_input.lower() == 'export':
            context_data = client.export_context()
            print("📤 上下文数据:")
            print(json.dumps(context_data, ensure_ascii=False, indent=2))
            continue
        elif user_input.lower().startswith('system '):
            new_system_msg = user_input[7:].strip()
            client.add_system_message(new_system_msg)
            print("✅ 系统提示已更新")
            continue
        elif user_input.lower() == 'switch':
            print(f"当前模型: {client.model}")
            new_model = input("输入新模型名称: ").strip()
            client.model = new_model
            print(f"✅ 已切换到: {client.model}")
            continue
        elif user_input.lower() == 'speed':
            speed = input("设置输出速度 (slow/normal/fast): ").strip().lower()
            client.set_stream_speed(speed)
            continue
        elif user_input.lower() == 'test':
            client.test_tool_capabilities()
            continue
        elif not user_input:
            continue

        # 开始聊天
        client.improved_stream_chat(user_input)


if __name__ == "__main__":
    main()