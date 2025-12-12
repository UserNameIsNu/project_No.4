import requests
import json
import time
import re


class OllamaChatClient:
    def __init__(self, base_url="http://localhost:11434", model="deepseek-r1:8b"):
        self.base_url = base_url
        self.model = model
        self.conversation_history = []
        self.available_tools = self._define_tools()

        # 流式输出配置
        self.stream_config = {
            'char_delay': 0.08,  # 字符延迟（秒）
            'sentence_delay': 0.3,  # 句子间延迟（秒）
            'comma_delay': 0.15,  # 逗号延迟（秒）
        }

        # 检查服务状态
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
            response = requests.get(f"{self.base_url}/api/tags", timeout=10)
            if response.status_code == 200:
                print("✅ Ollama服务连接正常")
                models = response.json().get('models', [])
                print(f"📚 可用模型: {[model['name'] for model in models]}")
                return True
            else:
                print(f"❌ Ollama服务异常，状态码: {response.status_code}")
                return False
        except requests.exceptions.ConnectionError:
            print("❌ 无法连接到Ollama服务，请确保Ollama正在运行")
            return False
        except Exception as e:
            print(f"❌ 检查服务时出错: {e}")
            return False

    def _define_tools(self):
        """定义可用的工具方法"""
        return [
            {
                "type": "function",
                "function": {
                    "name": "get_current_time",
                    "description": "获取当前系统时间",
                    "parameters": {
                        "type": "object",
                        "properties": {},
                        "required": []
                    }
                }
            },
            {
                "type": "function",
                "function": {
                    "name": "calculate_math",
                    "description": "计算数学表达式",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "expression": {
                                "type": "string",
                                "description": "数学表达式，如 2+2, 3*5, 10/2"
                            }
                        },
                        "required": ["expression"]
                    }
                }
            },
            {
                "type": "function",
                "function": {
                    "name": "get_weather",
                    "description": "获取天气信息",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "city": {
                                "type": "string",
                                "description": "城市名称"
                            }
                        },
                        "required": ["city"]
                    }
                }
            }
        ]

    def improved_stream_chat(self, message, use_native_tools=True):
        """改进的流式聊天方法"""
        try:
            # 如果使用原生工具调用且是8B模型，尝试使用工具调用
            if use_native_tools and "8b" in self.model:
                tool_response = self._try_native_tool_call(message)
                if tool_response:
                    return tool_response

            # 回退到手动工具检测
            tool_response = self._check_tool_usage(message)
            if tool_response:
                print("🤖 AI: ", end="", flush=True)
                self._simulate_stream_output(tool_response)
                return tool_response

            # 构建对话历史
            if self.conversation_history:
                messages = self.conversation_history + [{"role": "user", "content": message}]
                data = {
                    "model": self.model,
                    "messages": messages,
                    "stream": True,
                    "options": {
                        "temperature": 0.7,
                        "top_p": 0.9,
                        "num_predict": 500,
                    }
                }
                endpoint = "/api/chat"
            else:
                data = {
                    "model": self.model,
                    "prompt": message,
                    "stream": True,
                    "options": {
                        "temperature": 0.7,
                        "top_p": 0.9,
                        "num_predict": 500,
                    }
                }
                endpoint = "/api/generate"

            print("🤖 AI: ", end="", flush=True)

            response = requests.post(
                f"{self.base_url}{endpoint}",
                json=data,
                stream=True,
                timeout=120
            )

            if response.status_code != 200:
                error_msg = f"API错误: {response.status_code} - {response.text}"
                print(error_msg)
                return error_msg

            full_response = ""

            for line in response.iter_lines():
                if line:
                    try:
                        chunk = json.loads(line)

                        if endpoint == "/api/chat":
                            if 'message' in chunk and 'content' in chunk['message']:
                                content = chunk['message']['content']
                                self._simulate_stream_output(content)
                                full_response += content

                            if chunk.get('done', False):
                                break

                        else:
                            if 'response' in chunk:
                                content = chunk['response']
                                self._simulate_stream_output(content)
                                full_response += content

                            if chunk.get('done', False):
                                break

                    except json.JSONDecodeError:
                        continue

            print()  # 换行

            # 保存到对话历史
            self.conversation_history.extend([
                {"role": "user", "content": message},
                {"role": "assistant", "content": full_response}
            ])

            return full_response

        except requests.exceptions.Timeout:
            error_msg = "⏰ 请求超时"
            print(error_msg)
            return error_msg
        except Exception as e:
            error_msg = f"❌ 请求失败: {e}"
            print(error_msg)
            return error_msg

    def _try_native_tool_call(self, message):
        """尝试使用原生工具调用（适用于8B模型）"""
        try:
            messages = self.conversation_history + [{"role": "user", "content": message}]

            data = {
                "model": self.model,
                "messages": messages,
                "tools": self.available_tools,
                "stream": True,
                "options": {
                    "temperature": 0.7,
                    "top_p": 0.9,
                }
            }

            response = requests.post(
                f"{self.base_url}/api/chat",
                json=data,
                stream=True,
                timeout=120
            )

            if response.status_code == 200:
                print("🎯 使用原生工具调用...")
                return self._handle_native_tool_stream(response, message)
            else:
                print(f"⚠️ 原生工具调用失败，回退到手动检测 (状态码: {response.status_code})")
                return None

        except Exception as e:
            print(f"⚠️ 原生工具调用异常: {e}，回退到手动检测")
            return None

    def _handle_native_tool_stream(self, response, user_message):
        """处理原生工具调用的流式响应"""
        full_response = ""
        tool_calls_detected = False

        print("🤖 AI: ", end="", flush=True)

        for line in response.iter_lines():
            if line:
                try:
                    chunk = json.loads(line)

                    # 检查工具调用
                    if 'message' in chunk and 'tool_calls' in chunk['message']:
                        tool_calls_detected = True
                        tool_calls = chunk['message']['tool_calls']

                        for tool_call in tool_calls:
                            tool_name = tool_call['function']['name']
                            tool_args = json.loads(tool_call['function']['arguments'])

                            # 执行工具
                            tool_result = self.execute_tool(tool_name, tool_args)

                            # 保存到历史
                            self.conversation_history.extend([
                                {"role": "user", "content": user_message},
                                {"role": "assistant", "content": "", "tool_calls": tool_calls},
                                {"role": "tool", "content": tool_result, "tool_call_id": tool_call.get('id', '')}
                            ])

                            # 获取最终回复
                            print(f"\n🔄 工具执行完成: {tool_result}")
                            final_response = self._get_final_response_after_tools()
                            return final_response

                    # 正常文本输出
                    if 'message' in chunk and 'content' in chunk['message']:
                        content = chunk['message']['content']
                        if content:
                            self._simulate_stream_output(content)
                            full_response += content

                    if chunk.get('done', False) and not tool_calls_detected:
                        self.conversation_history.extend([
                            {"role": "user", "content": user_message},
                            {"role": "assistant", "content": full_response}
                        ])
                        break

                except json.JSONDecodeError:
                    continue

        print()  # 换行
        return full_response

    def _get_final_response_after_tools(self):
        """在工具调用后获取完整回复"""
        data = {
            "model": self.model,
            "messages": self.conversation_history,
            "stream": True,
            "options": {
                "temperature": 0.7,
                "top_p": 0.9,
            }
        }

        response = requests.post(
            f"{self.base_url}/api/chat",
            json=data,
            stream=True,
            timeout=120
        )

        final_response = ""
        print("🤖 AI: ", end="", flush=True)

        for line in response.iter_lines():
            if line:
                try:
                    chunk = json.loads(line)
                    if 'message' in chunk and 'content' in chunk['message']:
                        content = chunk['message']['content']
                        self._simulate_stream_output(content)
                        final_response += content

                    if chunk.get('done', False):
                        # 更新对话历史
                        for msg in self.conversation_history:
                            if msg.get('role') == 'assistant' and not msg.get('content'):
                                msg['content'] = final_response
                        break

                except json.JSONDecodeError:
                    continue

        print()  # 换行
        return final_response

    def _check_tool_usage(self, message):
        """手动检查是否需要使用工具"""
        message_lower = message.lower()

        # 检查时间相关
        time_keywords = ['时间', '几点', '现在几点', '当前时间', '什么时候', '何时', 'today', 'time', 'now']
        if any(keyword in message_lower for keyword in time_keywords):
            current_time = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
            return f"根据系统时间，现在是 {current_time}"

        # 检查数学计算
        math_pattern = r'(\d+[\+\-\*\/]\d+|\d+\.\d+[\+\-\*\/]\d+\.\d+)'
        math_matches = re.findall(math_pattern, message)
        if math_matches:
            try:
                result = eval(math_matches[0])
                return f"计算结果: {math_matches[0]} = {result}"
            except:
                pass

        # 检查明显的计算问题
        calc_keywords = ['计算', '等于多少', '是多少', '算一下', 'calculate', 'compute']
        if any(keyword in message_lower for keyword in calc_keywords):
            numbers = re.findall(r'\d+', message)
            if len(numbers) >= 2:
                if '加' in message_lower or '+' in message:
                    result = int(numbers[0]) + int(numbers[1])
                    return f"计算结果: {numbers[0]} + {numbers[1]} = {result}"
                elif '减' in message_lower or '-' in message:
                    result = int(numbers[0]) - int(numbers[1])
                    return f"计算结果: {numbers[0]} - {numbers[1]} = {result}"
                elif '乘' in message_lower or '*' in message or '×' in message:
                    result = int(numbers[0]) * int(numbers[1])
                    return f"计算结果: {numbers[0]} × {numbers[1]} = {result}"
                elif '除' in message_lower or '/' in message or '÷' in message:
                    if int(numbers[1]) != 0:
                        result = int(numbers[0]) / int(numbers[1])
                        return f"计算结果: {numbers[0]} ÷ {numbers[1]} = {result:.2f}"
                    else:
                        return "错误: 除数不能为零"

        # 检查天气查询
        weather_keywords = ['天气', 'weather', '气温', '温度']
        if any(keyword in message_lower for keyword in weather_keywords):
            # 简单提取城市名（实际应用中可以用更复杂的方法）
            cities = ['北京', '上海', '广州', '深圳', '杭州', '南京', '成都', '武汉']
            for city in cities:
                if city in message:
                    return f"{city}的天气: 晴朗，25°C，微风"
            return "请指定要查询天气的城市"

        return None

    def _simulate_stream_output(self, text):
        """模拟流式输出（增加延迟）"""
        for char in text:
            print(char, end="", flush=True)

            # 根据字符类型设置不同延迟
            if char in '。！？.!?':  # 句子结束标点
                time.sleep(self.stream_config['sentence_delay'])
            elif char in '，,；;':  # 逗号分号
                time.sleep(self.stream_config['comma_delay'])
            else:  # 普通字符
                time.sleep(self.stream_config['char_delay'])

    def execute_tool(self, tool_name, arguments):
        """执行工具方法"""
        print(f"\n🔧 调用工具: {tool_name}, 参数: {arguments}")

        if tool_name == "get_current_time":
            current_time = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
            return f"当前时间是: {current_time}"

        elif tool_name == "calculate_math":
            try:
                expression = arguments.get("expression", "")
                allowed_chars = set('0123456789+-*/.() ')
                if all(c in allowed_chars for c in expression):
                    result = eval(expression)
                    return f"计算结果: {expression} = {result}"
                else:
                    return "错误: 表达式包含不安全字符"
            except Exception as e:
                return f"计算错误: {e}"

        elif tool_name == "get_weather":
            city = arguments.get("city", "未知城市")
            return f"{city}的天气: 晴朗，25°C，微风"

        else:
            return f"未知工具: {tool_name}"

    def clear_history(self):
        """清空对话历史"""
        self.conversation_history = []
        print("🗑️ 对话历史已清空")

    def show_conversation(self):
        """显示对话历史"""
        if not self.conversation_history:
            print("📜 对话历史为空")
            return

        print("\n📜 对话历史:")
        for i, msg in enumerate(self.conversation_history):
            role = msg['role']
            content = msg.get('content', '')
            tool_calls = msg.get('tool_calls', None)

            if role == 'user':
                print(f"👤 [{i}]: {content}")
            elif role == 'assistant':
                if tool_calls:
                    print(f"🤖 [{i}]: [工具调用] {content}")
                else:
                    print(f"🤖 [{i}]: {content}")
            elif role == 'tool':
                print(f"🔧 [{i}]: 工具执行结果: {content}")

    def test_tool_capabilities(self):
        """测试工具调用能力"""
        print("\n🧪 测试工具调用...")

        test_cases = [
            "现在几点了？",
            "计算一下 15 * 8 等于多少",
            "北京的天气怎么样？",
            "请介绍一下人工智能"
        ]

        for i, test_case in enumerate(test_cases, 1):
            print(f"\n{i}. 测试: '{test_case}'")
            self.improved_stream_chat(test_case)


def main():
    # 初始化客户端，默认使用8B模型
    client = OllamaChatClient(model="deepseek-r1:8b")

    print("\n=== Ollama聊天客户端 (8B模型) ===")
    print("🔧 支持工具调用: 时间查询、数学计算、天气查询")
    print("⚡ 流式输出速度: 可调节")

    # 设置输出速度
    speed = input("设置输出速度 (slow/normal/fast, 默认normal): ").strip().lower()
    if speed in ['slow', 'normal', 'fast']:
        client.set_stream_speed(speed)
    else:
        client.set_stream_speed('normal')

    # 测试工具能力
    client.test_tool_capabilities()

    print("\n📝 可用命令:")
    print("  - 'clear': 清空对话历史")
    print("  - 'history': 显示对话历史")
    print("  - 'quit/exit/退出': 退出程序")
    print("  - 'switch': 切换模型")
    print("  - 'speed': 调整输出速度")
    print("  - 'test': 重新测试工具能力")

    while True:
        user_input = input("\n👤 你: ").strip()

        if user_input.lower() in ['quit', 'exit', '退出']:
            break
        elif user_input.lower() == 'clear':
            client.clear_history()
            continue
        elif user_input.lower() == 'history':
            client.show_conversation()
            continue
        elif user_input.lower() == 'switch':
            print(f"当前模型: {client.model}")
            new_model = input("输入新模型名称 (deepseek-r1:1.5b 或 deepseek-r1:8b): ").strip()
            if new_model in ['deepseek-r1:1.5b', 'deepseek-r1:8b']:
                client.model = new_model
                print(f"✅ 已切换到: {client.model}")
                client.test_tool_capabilities()
            else:
                print("❌ 无效的模型名称")
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