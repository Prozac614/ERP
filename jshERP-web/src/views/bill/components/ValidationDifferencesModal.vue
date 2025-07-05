<template>
  <a-modal
    title="校验差异详情"
    :width="800"
    :visible="visible"
    :footer="null"
    @cancel="handleCancel"
  >
    <div>
      <a-alert
        message="校验失败"
        description="发现以下单据内容不一致，请核实后重新录入单据。"
        type="error"
        show-icon
        style="margin-bottom: 16px"
      />
      
      <a-table
        :columns="columns"
        :data-source="differences"
        :pagination="false"
        row-key="diffType"
        size="small"
      >
        <template slot="diffTypeName" slot-scope="text">
          <a-tag color="red">{{ text }}</a-tag>
        </template>
        <template slot="users" slot-scope="text">
          <a-tag v-for="user in text.split(',')" :key="user" color="blue">
            {{ user.trim() }}
          </a-tag>
        </template>
      </a-table>
      
      <div style="text-align: center; margin-top: 16px;">
        <a-button type="primary" @click="handleCancel">
          确定
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
export default {
  name: 'ValidationDifferencesModal',
  data() {
    return {
      visible: false,
      differences: [],
      columns: [
        {
          title: '差异类型',
          dataIndex: 'diffTypeName',
          width: 120,
          scopedSlots: { customRender: 'diffTypeName' }
        },
        {
          title: '差异描述',
          dataIndex: 'description',
          width: 300
        },
        {
          title: '涉及用户',
          dataIndex: 'users',
          width: 150,
          scopedSlots: { customRender: 'users' }
        },
        {
          title: '影响单据',
          dataIndex: 'affectedBills',
          width: 80
        }
      ]
    }
  },
  methods: {
    show(differences) {
      this.visible = true
      this.differences = differences || []
    },
    
    handleCancel() {
      this.visible = false
      this.differences = []
    }
  }
}
</script> 