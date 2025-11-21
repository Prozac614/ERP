function resolveErrorMessage(payload, fallback) {
  if (!payload) {
    return fallback
  }
  if (typeof payload === 'string') {
    const trimmed = payload.trim()
    if (!trimmed) {
      return fallback
    }
    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
      try {
        const parsed = JSON.parse(trimmed)
        return resolveErrorMessage(parsed, fallback)
      } catch (e) {
        return trimmed
      }
    }
    return trimmed
  }
  if (payload.msg) {
    if (typeof payload.msg === 'string') {
      return resolveErrorMessage(payload.msg, fallback)
    }
    return resolveErrorMessage(payload.msg, fallback)
  }

  if (payload.message) {
    if (typeof payload.message === 'string') {
      return resolveErrorMessage(payload.message, fallback)
    }
    return resolveErrorMessage(payload.message, fallback)
  }
  if (payload.data) {
    if (typeof payload.data === 'string') {
      return payload.data || fallback
    }
    return resolveErrorMessage(payload.data, fallback)
  }
  if (payload.response && payload.response.data) {
    return resolveErrorMessage(payload.response.data, fallback)
  }
  return fallback
}

import JEditableTable from '@/components/jeecg/JEditableTable'
import { VALIDATE_NO_PASSED, getRefPromise, validateFormAndTables } from '@/utils/JEditableTableUtil'
import { httpAction, getAction } from '@/api/manage'

export const JEditableTableMixin = {
  components: {
    JEditableTable
  },
  data() {
    return {
      title: '操作',
      visible: false,
      form: this.$form.createForm(this),
      confirmLoading: false,
      model: {},
      labelCol: {
        xs: { span: 24 },
        sm: { span: 6 }
      },
      wrapperCol: {
        xs: { span: 24 },
        sm: { span: 18 }
      }
    }
  },
  methods: {

    /** 获取所有的editableTable实例 */
    getAllTable() {
      if (!(this.refKeys instanceof Array)) {
        throw this.throwNotArray('refKeys')
      }
      let values = this.refKeys.map(key => getRefPromise(this, key))
      return Promise.all(values)
    },

    /** 遍历所有的JEditableTable实例 */
    eachAllTable(callback) {
      // 开始遍历
      this.getAllTable().then(tables => {
        tables.forEach((item, index) => {
          if (typeof callback === 'function') {
            callback(item, index)
          }
        })
      })
    },

    /** 当点击新增按钮时调用此方法 */
    add() {
      if (typeof this.addBefore === 'function') this.addBefore()
      // 默认新增空数据
      let rowNum = this.addDefaultRowNum
      if (typeof rowNum !== 'number') {
        rowNum = 1
        console.warn('由于你没有在 data 中定义 addDefaultRowNum 或 addDefaultRowNum 不是数字，所以默认添加一条空数据，如果不想默认添加空数据，请将定义 addDefaultRowNum 为 0')
      }
      this.eachAllTable((item) => {
        item.add(rowNum)
      })
      if (typeof this.addAfter === 'function') this.addAfter(this.model)
      this.edit({})
    },
    /** 当点击了编辑（修改）按钮时调用此方法 */
    edit(record) {
      if (typeof this.editBefore === 'function') this.editBefore(record)
      this.visible = true
      this.activeKey = this.refKeys[0]
      this.model = Object.assign({}, record)
      this.$nextTick(() => {
        this.form.resetFields()
        if (typeof this.editAfter === 'function') this.editAfter(this.model)
      })
    },
    /** 关闭弹窗，并将所有JEditableTable实例回归到初始状态 */
    close() {
      this.visible = false
      this.eachAllTable((item) => {
        item.initialize()
      })
      this.$emit('close')
    },

    /** 查询某个tab的数据 */
    requestSubTableData(url, params, tab, success) {
      tab.loading = true
      getAction(url, params).then(res => {
        if (res && res.code === 200) {
          tab.dataSource = res.data.rows
          typeof success === 'function' ? success(res) : ''
        }
      }).finally(() => {
        tab.loading = false
      })
    },
    /** 发起请求，自动判断是执行新增还是修改操作 */
    request(formData) {
      let url = this.url.add, method = 'post'
      if (this.model.id) {
        url = this.url.edit
        method = 'put'
      }
      this.confirmLoading = true
      httpAction(url, formData, method).then((res) => {
        if (res.code === 200) {
          this.$emit('ok')
          this.confirmLoading = false
          this.close()
        } else {
          const message = resolveErrorMessage(res, '操作失败，请稍后重试')
          // 使用信息对话框替代消息提示
          if (this.$info && typeof this.$info === 'function') {
            this.$info({
              title: '验证失败',
              content: message,
              okText: '知道了',
              centered: true
            })
          } else if (this.$message && typeof this.$message.warning === 'function') {
            // 降级方案：使用 message
            this.$message.warning(message)
          }
          this.confirmLoading = false
        }
      }).catch(error => {
        let message = resolveErrorMessage(error, '')
        if (!message && error && error.message) {
          message = error.message
        }
        if (!message) {
          message = '请求失败，请稍后重试'
        }
        if (this.$message && typeof this.$message.error === 'function') {
          this.$message.error(message)
        } else {
          console.error(message)
        }
        this.confirmLoading = false
      })
    },

    /* --- handle 事件 --- */

    /** ATab 选项卡切换事件 */
    handleChangeTabs(key) {
      // 自动重置scrollTop状态，防止出现白屏
      getRefPromise(this, key).then(editableTable => {
        editableTable.resetScrollTop()
      })
    },
    /** 关闭按钮点击事件 */
    handleCancel() {
      // 检查是否需要显示保存确认对话框
      const needConfirm = this.shouldShowSaveConfirmation && this.shouldShowSaveConfirmation()

      if (needConfirm) {
        const that = this
        this.$confirm({
          title: '提示',
          content: '是否需要保存？',
          okText: '是',
          cancelText: '否',
          onOk() {
            // 用户选择保存，调用保存方法
            // handleOk会在保存成功后自动关闭弹窗
            that.handleOk()
          },
          onCancel() {
            // 用户选择不保存，直接关闭
            that.close()
          }
        })
      } else {
        // 不需要确认，直接关闭
        this.close()
      }
    },
    /** 确定按钮点击事件 */
    handleOk() {
      /** 触发表单验证 */
      this.getAllTable().then(tables => {
        /** 一次性验证主表和所有的次表 */
        return validateFormAndTables(this.form, tables)
      }).then(allValues => {
        if (typeof this.classifyIntoFormData !== 'function') {
          throw this.throwNotFunction('classifyIntoFormData')
        }
        let formData = this.classifyIntoFormData(allValues)
        // 发起请求
        return this.request(formData)
      }).catch(e => {
        if (e.error === VALIDATE_NO_PASSED) {
          // 如果有未通过表单验证的子表，就自动跳转到它所在的tab
          this.activeKey = e.index == null ? this.activeKey : this.refKeys[e.index]
          
          // 如果有错误行信息，滚动到该行
          if (typeof e.firstErrorRowIndex === 'number' && e.firstErrorRowIndex >= 0) {
            this.scrollToErrorRow(e.index, e.firstErrorRowIndex)
          }
        } else {
          console.error(e)
        }
      })
    },

    /** 滚动到错误行 */
    scrollToErrorRow(tableIndex, rowIndex) {
      // 使用防抖延迟，等待DOM更新
      setTimeout(() => {
        const refKey = this.refKeys[tableIndex != null ? tableIndex : 0]
        const tableRef = this.$refs[refKey]
        if (tableRef && typeof tableRef.resetScrollTop === 'function') {
          // 计算滚动位置，行高为42px
          const scrollTop = rowIndex * 42
          tableRef.resetScrollTop(scrollTop)
        }
      }, 300)
    },

    /* --- throw --- */

    /** not a function */
    throwNotFunction(name) {
      return `${name} 未定义或不是一个函数`
    },

    /** not a array */
    throwNotArray(name) {
      return `${name} 未定义或不是一个数组`
    }

  }
}