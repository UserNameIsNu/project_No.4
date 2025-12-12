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

        # 上下文管理配置
        self.context_config = {
            'max_history_length': 20,  # 最大对话轮数
            'max_tokens': 4000,  # 估计的上下文token限制
            'enable_context': True,  # 是否启用上下文
        }

        # 流式输出配置
        self.stream_config = {
            'char_delay': 0.08,
            'sentence_delay': 0.3,
            'comma_delay': 0.15,
        }

        self._check_service()

    def manage_context(self, new_message):
        """智能管理上下文，防止超过限制"""
        if not self.context_config['enable_context']:
            return [{"role": "user", "content": new_message}]

        # 添加新消息到历史
        self.conversation_history.append({"role": "user", "content": new_message})

        # 如果历史太长，进行截断
        if len(self.conversation_history) > self.context_config['max_history_length']:
            # 保留系统提示和最近对话，删除中间部分
            keep_messages = 6  # 保留最近3轮对话
            if len(self.conversation_history) > keep_messages:
                # 保留前2条（如果有系统消息）和后keep_messages条
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
        # 如果已经有系统消息，替换它
        if self.conversation_history and self.conversation_history[0].get("role") == "system":
            self.conversation_history[0] = system_message
        else:
            self.conversation_history.insert(0, system_message)

    def improved_stream_chat(self, message, use_native_tools=True):
        """改进的流式聊天方法，包含上下文管理"""
        try:
            # 管理上下文
            current_messages = self.manage_context(message)

            # 如果使用原生工具调用且是8B模型，尝试使用工具调用
            if use_native_tools and "8b" in self.model:
                tool_response = self._try_native_tool_call(message, current_messages)
                if tool_response:
                    return tool_response

            # 回退到手动工具检测
            tool_response = self._check_tool_usage(message)
            if tool_response:
                print("🤖 AI: ", end="", flush=True)
                self._simulate_stream_output(tool_response)
                # 将工具响应也添加到历史中
                self.conversation_history.append({"role": "assistant", "content": tool_response})
                return tool_response

            # 使用聊天端点（支持消息数组）
            data = {
                "model": self.model,
                "messages": current_messages,
                "stream": True,
                "options": {
                    "temperature": 0.7,
                    "top_p": 0.9,
                    "num_predict": 500,
                }
            }

            print("🤖 AI: ", end="", flush=True)

            response = requests.post(
                f"{self.base_url}/api/chat",
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

                        if 'message' in chunk and 'content' in chunk['message']:
                            content = chunk['message']['content']
                            self._simulate_stream_output(content)
                            full_response += content

                        if chunk.get('done', False):
                            break

                    except json.JSONDecodeError:
                        continue

            print()  # 换行

            # 将助手回复添加到历史中（用户消息已经在manage_context中添加）
            self.conversation_history.append({"role": "assistant", "content": full_response})

            return full_response

        except requests.exceptions.Timeout:
            error_msg = "⏰ 请求超时"
            print(error_msg)
            return error_msg
        except Exception as e:
            error_msg = f"❌ 请求失败: {e}"
            print(error_msg)
            return error_msg

    def clear_context(self, keep_system_message=True):
        """清空对话上下文"""
        if keep_system_message and self.conversation_history:
            # 保留系统消息
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

        print(f"\n📊 上下文信息:")
        print(f"   总消息数: {total_messages}")
        print(f"   用户消息: {user_messages}")
        print(f"   助手回复: {assistant_messages}")
        print(f"   系统消息: {system_messages}")
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

    # 其他方法保持不变（_try_native_tool_call, _check_tool_usage, _simulate_stream_output等）


def main():
    # 初始化客户端
    client = OllamaChatClient(model="deepseek-r1:8b")

    print("\n=== Ollama聊天客户端 (带上下文管理) ===")

    # 添加系统提示
    system_prompt = """你是一个有用的AI助手。请用中文回答用户的问题，保持友好和专业的语气。
如果用户询问时间、天气或需要进行计算，请使用相应的工具来获取准确信息。"""
    client.add_system_message(system_prompt)

    # 设置输出速度
    client.set_stream_speed('normal')

    print("🔧 功能特性:")
    print("   ✅ 智能上下文管理")
    print("   ✅ 自动历史截断")
    print("   ✅ 系统提示设置")
    print("   ✅ 上下文导入导出")

    print("\n📝 可用命令:")
    print("  - 'clear': 清空对话历史")
    print("  - 'context': 显示上下文信息")
    print("  - 'export': 导出对话上下文")
    print("  - 'system <消息>': 设置系统提示")
    print("  - 'quit/exit/退出': 退出程序")
    print("  - 'switch': 切换模型")
    print("  - 'speed': 调整输出速度")

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
        elif not user_input:
            continue

        # 开始聊天（自动管理上下文）
        client.improved_stream_chat(user_input)


if __name__ == "__main__":
    main()