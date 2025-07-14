<template>
  <a-modal
    title="选择校验用户"
    :width="600"
    :visible="visible"
    :confirmLoading="confirmLoading"
    @ok="handleOk"
    @cancel="handleCancel"
    okText="发起校验"
    cancelText="取消"
  >
    <div>
      <p>今日有以下用户保存了销售出库单据，请选择要进行校验的用户：</p>
      <a-table
        :columns="columns"
        :data-source="userList"
        :pagination="false"
        :row-selection="{ selectedRowKeys: selectedUserIds, onChange: onSelectChange }"
        row-key="userId"
        size="small"
      >
        <template slot="realName" slot-scope="text, record">
          {{ text || record.userName }}
        </template>
        <template slot="billTime" slot-scope="text, record">
          {{ formatTimeRange(record.firstBillTime, record.lastBillTime) }}
        </template>
      </a-table>
    </div>
  </a-modal>
</template>

<script>
import { postAction } from '@/api/manage'

export default {
  name: 'UserSelectionModal',
  data() {
    return {
      visible: false,
      confirmLoading: false,
      userList: [],
      selectedUserIds: [],
      currentUserIds: [],
      validationDate: null,
      columns: [
        {
          title: '用户名',
          dataIndex: 'userName',
          width: 120
        },
        {
          title: '真实姓名',
          dataIndex: 'realName',
          width: 120,
          scopedSlots: { customRender: 'realName' }
        },
        {
          title: '单据数量',
          dataIndex: 'billCount',
          width: 80
        },
        {
          title: '单据时间',
          dataIndex: 'billTime',
          width: 180,
          scopedSlots: { customRender: 'billTime' }
        }
      ]
    }
  },
  methods: {
    show(data, validationDate) {
      this.visible = true
      this.userList = data.otherUsers || []
      this.currentUserIds = data.currentUserIds || []
      this.selectedUserIds = []
      this.validationDate = validationDate
    },
    
    handleOk() {
      if (this.selectedUserIds.length === 0) {
        this.$message.warning('请选择至少一个用户进行校验！')
        return
      }
      
      this.confirmLoading = true
      const request = {
        currentUserIds: this.currentUserIds,
        selectedUserIds: this.selectedUserIds,
        validationDate: this.validationDate
      }
      
      postAction('/depotHead/performCrossValidation', request).then((res) => {
        console.log('performCrossValidation响应:', res);
        if (res.code === 200) {
          this.handleValidationResult(res.data)
        } else {
          this.$message.error(res.msg || '校验失败')
        }
      }).catch((error) => {
        console.error('performCrossValidation请求错误:', error);
        this.$message.error('校验请求失败')
      }).finally(() => {
        this.confirmLoading = false
      })
    },
    
    handleCancel() {
      this.visible = false
      this.selectedUserIds = []
      this.userList = []
      this.currentUserIds = []
      this.validationDate = null
    },
    
    onSelectChange(selectedRowKeys) {
      this.selectedUserIds = selectedRowKeys
    },
    
    handleValidationResult(result) {
      console.log('===== UserSelectionModal.handleValidationResult 被调用 =====');
      console.log('校验结果 result:', result);
      console.log('result类型:', typeof result);
      
      // 兼容处理字段名（可能是 consistent 或 isConsistent）
      const isConsistent = result.isConsistent !== undefined ? result.isConsistent : result.consistent;
      console.log('是否一致 isConsistent:', isConsistent);
      console.log('差异数据 result.differences:', result.differences);
      
      if (isConsistent) {
        console.log('校验通过，发送validation-success事件');
        this.$message.success(`校验通过！共有 ${result.totalBills} 种商品数据一致，相关单据状态已自动更新。`)
        this.visible = false
        this.$emit('validation-success', result)
      } else {
        console.log('校验失败，即将显示差异');
        // 显示校验差异
        this.showValidationDifferences(result.differences)
      }
    },
    
    showValidationDifferences(differences) {
      // TODO: 显示校验差异界面，后续在阶段二完善
      console.log('===== UserSelectionModal.showValidationDifferences 被调用 =====');
      console.log('传入的differences:', differences);
      console.log('differences类型:', typeof differences);
      console.log('differences长度:', differences ? differences.length : 'undefined');
      
      console.log('发送validation-failed事件到父组件');
      // 发送校验失败事件，让父组件处理差异显示
      this.$emit('validation-failed', differences)
      this.visible = false
    },
    
    formatTimeRange(firstTime, lastTime) {
      if (!firstTime) return ''
      const first = new Date(firstTime).toLocaleTimeString()
      const last = lastTime ? new Date(lastTime).toLocaleTimeString() : ''
      return first === last ? first : `${first} - ${last}`
    }
  }
}
</script> 