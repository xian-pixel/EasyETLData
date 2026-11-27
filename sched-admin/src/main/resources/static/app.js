(function () {
    'use strict';

    /* ==================== 通用工具 ==================== */

    const GENDER = { 0: '未知', 1: '男', 2: '女' };
    const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp'];
    // 仅 gender 需要转数字；各类 ID 一律保持字符串，避免雪花 ID 精度丢失
    const NUMBER_FIELDS = new Set(['gender']);

    const $ = (selector, root) => (root || document).querySelector(selector);
    const $$ = (selector, root) => Array.from((root || document).querySelectorAll(selector));

    /**
     * 无损解析：后端返回的雪花 ID 是 19 位 long，直接 JSON.parse 会丢精度。
     * 这里先把"值位置上的 16 位以上整数"加上引号再解析，字符串内部不受影响。
     */
    function parseJsonPreserveLong(text) {
        const safe = text.replace(
            /("(?:[^"\\]|\\.)*")|(:\s*)(-?\d{16,})(?=\s*[,}\]])/g,
            (match, str, colon, num) => (str ? str : colon + '"' + num + '"')
        );
        return JSON.parse(safe);
    }

    /** 统一请求：后端返回 {code,msg,data}，code === 0 视为成功 */
    async function request(url, options) {
        let resp;
        try {
            resp = await fetch(url, options);
        } catch (e) {
            throw new Error('网络请求失败，请确认后端已启动，并通过 http://localhost:8080/index.html 访问本页');
        }
        const text = await resp.text();
        let body = null;
        if (text) {
            try {
                body = parseJsonPreserveLong(text);
            } catch (e) {
                throw new Error('响应不是合法 JSON（HTTP ' + resp.status + '）');
            }
        }
        if (!body) {
            throw new Error('服务返回空响应（HTTP ' + resp.status + '）');
        }
        if (body.code !== 0) {
            throw new Error(body.msg || ('请求失败（' + body.code + '）'));
        }
        return body.data;
    }

    const api = {
        get: (url) => request(url),
        post: (url, data) => request(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data || {})
        }),
        put: (url, data) => request(url, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data || {})
        }),
        del: (url) => request(url, { method: 'DELETE' }),
        upload: (url, formData) => request(url, { method: 'POST', body: formData })
    };

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    let toastTimer = null;

    function toast(message, isError) {
        const el = $('#toast');
        el.textContent = message;
        el.className = 'toast' + (isError ? ' error' : '');
        el.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
            el.hidden = true;
        }, isError ? 4500 : 2200);
    }

    /** 表单转对象：空字符串不发（避免触发后端的格式校验），数字字段转 Number */
    function formToObject(form) {
        const data = {};
        new FormData(form).forEach((raw, key) => {
            if (typeof raw !== 'string') {
                data[key] = raw;
                return;
            }
            const value = raw.trim();
            if (value === '') {
                return;
            }
            data[key] = NUMBER_FIELDS.has(key) ? Number(value) : value;
        });
        return data;
    }

    function fillForm(form, data) {
        Object.entries(data || {}).forEach(([key, value]) => {
            const field = form.elements[key];
            if (field) {
                field.value = value == null ? '' : value;
            }
        });
    }

    function formatTime(value) {
        if (!value) {
            return '-';
        }
        return String(value).replace('T', ' ').slice(0, 19);
    }

    function formatSize(bytes) {
        if (bytes == null) {
            return '-';
        }
        if (bytes < 1024) {
            return bytes + ' B';
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024).toFixed(1) + ' KB';
        }
        return (bytes / 1024 / 1024).toFixed(2) + ' MB';
    }

    /* ==================== 标签页 ==================== */

    function bindTabs() {
        $$('.tab').forEach((btn) => {
            btn.addEventListener('click', () => {
                $$('.tab').forEach((item) => item.classList.toggle('active', item === btn));
                const name = btn.dataset.tab;
                $$('.panel').forEach((panel) => {
                    panel.hidden = panel.id !== 'panel-' + name;
                });
                if (name === 'users') {
                    loadUsers();
                }
                if (name === 'files') {
                    loadFiles();
                }
            });
        });
    }

    /* ==================== 我的资料 ==================== */

    function renderMe(me) {
        $('#me-badge').textContent = (me.nickname || me.username || '未知用户') + '（ID ' + me.userId + '）';
        const rows = [
            ['用户ID', me.userId],
            ['登录名', me.username],
            ['昵称', me.nickname],
            ['真实姓名', me.realName],
            ['手机号', me.mobile],
            ['邮箱', me.email],
            ['性别', GENDER[me.gender]],
            ['部门', me.deptName],
            ['简介', me.profile],
            ['状态', me.status === 1 ? '启用' : '禁用'],
            ['创建时间', formatTime(me.createTime)]
        ];
        $('#me-info').innerHTML = rows.map(([key, value]) =>
            '<dt>' + escapeHtml(key) + '</dt><dd>'
            + escapeHtml(value == null || value === '' ? '-' : value) + '</dd>').join('');

        const avatar = $('#me-avatar');
        if (me.avatarFileId) {
            avatar.textContent = '';
            avatar.style.backgroundImage = 'url("/api/file/' + encodeURIComponent(me.avatarFileId) + '/preview?t=' + Date.now() + '")';
        } else {
            avatar.textContent = '无头像';
            avatar.style.backgroundImage = '';
        }
    }

    async function loadMe(silent) {
        try {
            const me = await api.get('/api/user/me');
            renderMe(me);
            fillForm($('#form-me'), {
                nickname: me.nickname,
                realName: me.realName,
                mobile: me.mobile,
                email: me.email,
                gender: me.gender == null ? 0 : me.gender,
                deptName: me.deptName,
                profile: me.profile
            });
            if (!silent) {
                toast('当前身份已加载');
            }
            return me;
        } catch (e) {
            $('#me-info').innerHTML = '<dd class="muted">' + escapeHtml(e.message) + '</dd>';
            $('#me-badge').textContent = '未就绪';
            toast(e.message, true);
            return null;
        }
    }

    function bindMine() {
        $('#btn-refresh-me').addEventListener('click', () => loadMe(false));

        $('#form-me').addEventListener('submit', async (e) => {
            e.preventDefault();
            try {
                await api.put('/api/user/me', formToObject(e.target));
                toast('资料已保存');
                await loadMe(true);
            } catch (err) {
                toast(err.message, true);
            }
        });

        $('#form-password').addEventListener('submit', async (e) => {
            e.preventDefault();
            const data = formToObject(e.target);
            if (!data.oldPassword || !data.newPassword) {
                toast('请填写原密码与新密码', true);
                return;
            }
            try {
                await api.put('/api/user/me/password', data);
                e.target.reset();
                toast('密码已修改');
            } catch (err) {
                toast(err.message, true);
            }
        });

        $('#avatar-input').addEventListener('change', async (e) => {
            const file = e.target.files && e.target.files[0];
            if (!file) {
                return;
            }
            const formData = new FormData();
            formData.append('file', file);
            try {
                const me = await api.upload('/api/user/me/avatar', formData);
                renderMe(me);
                toast('头像已更新');
            } catch (err) {
                toast(err.message, true);
            } finally {
                e.target.value = '';
            }
        });
    }

    /* ==================== 用户管理 ==================== */

    const userState = { pageNum: 1, pageSize: 10 };

    function renderUsers(page) {
        const tbody = $('#user-tbody');
        const records = page.records || [];
        if (!records.length) {
            tbody.innerHTML = '<tr><td class="empty" colspan="8">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = records.map((user) => '<tr>'
                + '<td>' + escapeHtml(user.userId) + '</td>'
                + '<td>' + escapeHtml(user.username) + '</td>'
                + '<td>' + escapeHtml(user.nickname || '-') + '</td>'
                + '<td>' + escapeHtml(user.realName || '-') + '</td>'
                + '<td>' + escapeHtml(user.mobile || '-') + '</td>'
                + '<td>' + (user.status === 1 ? '启用' : '禁用') + '</td>'
                + '<td>' + escapeHtml(formatTime(user.createTime)) + '</td>'
                + '<td>'
                + '<button type="button" class="btn link" data-action="detail" data-id="' + escapeHtml(user.userId) + '">详情</button>'
                + '<button type="button" class="btn link" data-action="edit" data-id="' + escapeHtml(user.userId) + '">编辑</button>'
                + '<button type="button" class="btn link danger" data-action="delete" data-id="' + escapeHtml(user.userId) + '">删除</button>'
                + '</td></tr>').join('');
        }
        const current = page.current || 1;
        const pages = page.pages || 1;
        $('#user-page-info').textContent = '第 ' + current + ' / ' + pages + ' 页，共 ' + (page.total || 0) + ' 条';
        $('#user-prev').disabled = current <= 1;
        $('#user-next').disabled = current >= pages;
    }

    async function loadUsers() {
        const params = new URLSearchParams({
            pageNum: String(userState.pageNum),
            pageSize: String(userState.pageSize)
        });
        const keyword = $('#user-keyword').value.trim();
        if (keyword) {
            params.set('keyword', keyword);
        }
        try {
            renderUsers(await api.get('/api/user/page?' + params.toString()));
        } catch (err) {
            $('#user-tbody').innerHTML = '<tr><td class="empty" colspan="8">' + escapeHtml(err.message) + '</td></tr>';
            toast(err.message, true);
        }
    }

    function bindUsers() {
        $('#form-create-user').addEventListener('submit', async (e) => {
            e.preventDefault();
            try {
                const created = await api.post('/api/user', formToObject(e.target));
                toast('创建成功，用户ID：' + created.userId);
                e.target.reset();
                userState.pageNum = 1;
                loadUsers();
            } catch (err) {
                toast(err.message, true);
            }
        });

        $('#btn-search-user').addEventListener('click', () => {
            userState.pageNum = 1;
            loadUsers();
        });

        $('#user-keyword').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                userState.pageNum = 1;
                loadUsers();
            }
        });

        $('#user-prev').addEventListener('click', () => {
            if (userState.pageNum > 1) {
                userState.pageNum -= 1;
                loadUsers();
            }
        });

        $('#user-next').addEventListener('click', () => {
            userState.pageNum += 1;
            loadUsers();
        });

        $('#btn-close-detail').addEventListener('click', () => {
            $('#detail-card').hidden = true;
        });

        $('#user-tbody').addEventListener('click', async (e) => {
            const btn = e.target.closest('button[data-action]');
            if (!btn) {
                return;
            }
            const id = btn.dataset.id;
            const action = btn.dataset.action;
            try {
                if (action === 'detail') {
                    const user = await api.get('/api/user/' + encodeURIComponent(id));
                    $('#detail-pre').textContent = JSON.stringify(user, null, 2);
                    $('#detail-card').hidden = false;
                } else if (action === 'edit') {
                    const user = await api.get('/api/user/' + encodeURIComponent(id));
                    const form = $('#form-edit-user');
                    form.elements.userId.value = user.userId;
                    fillForm(form, {
                        nickname: user.nickname,
                        realName: user.realName,
                        mobile: user.mobile,
                        email: user.email,
                        gender: user.gender == null ? 0 : user.gender,
                        deptName: user.deptName,
                        profile: user.profile
                    });
                    $('#user-edit-dialog').showModal();
                } else if (action === 'delete') {
                    if (!window.confirm('确认删除用户 ' + id + '？将写入删除时间戳（逻辑删除）。')) {
                        return;
                    }
                    await api.del('/api/user/' + encodeURIComponent(id));
                    toast('已删除');
                    loadUsers();
                }
            } catch (err) {
                toast(err.message, true);
            }
        });

        $('#btn-close-edit').addEventListener('click', () => {
            $('#user-edit-dialog').close();
        });

        $('#form-edit-user').addEventListener('submit', async (e) => {
            e.preventDefault();
            const data = formToObject(e.target);
            const id = data.userId;
            delete data.userId;
            try {
                await api.put('/api/user/' + encodeURIComponent(id), data);
                $('#user-edit-dialog').close();
                toast('已保存');
                loadUsers();
                loadMe(true);
            } catch (err) {
                toast(err.message, true);
            }
        });
    }

    /* ==================== 文件中心 ==================== */

    const fileState = { pageNum: 1, pageSize: 10 };

    function renderFiles(page) {
        const tbody = $('#file-tbody');
        const records = page.records || [];
        if (!records.length) {
            tbody.innerHTML = '<tr><td class="empty" colspan="7">暂无文件</td></tr>';
        } else {
            tbody.innerHTML = records.map((file) => {
                const id = escapeHtml(file.fileId);
                const isImage = IMAGE_EXT.indexOf(String(file.ext || '').toLowerCase()) >= 0;
                return '<tr>'
                    + '<td>' + id + '</td>'
                    + '<td>' + escapeHtml(file.bizType) + '</td>'
                    + '<td>' + escapeHtml(file.originalName) + '</td>'
                    + '<td>' + escapeHtml(file.ext || '-') + '</td>'
                    + '<td>' + formatSize(file.fileSize) + '</td>'
                    + '<td>' + escapeHtml(formatTime(file.createTime)) + '</td>'
                    + '<td>'
                    + '<button type="button" class="btn link" data-action="preview" data-id="' + id + '"'
                    + (isImage ? '' : ' disabled title="仅图片支持在线预览"') + '>预览</button>'
                    + '<button type="button" class="btn link" data-action="download" data-id="' + id + '">下载</button>'
                    + '<button type="button" class="btn link danger" data-action="delete" data-id="' + id + '">删除</button>'
                    + '</td></tr>';
            }).join('');
        }
        const current = page.current || 1;
        const pages = page.pages || 1;
        $('#file-page-info').textContent = '第 ' + current + ' / ' + pages + ' 页，共 ' + (page.total || 0) + ' 条';
        $('#file-prev').disabled = current <= 1;
        $('#file-next').disabled = current >= pages;
    }

    async function loadFiles() {
        const params = new URLSearchParams({
            pageNum: String(fileState.pageNum),
            pageSize: String(fileState.pageSize)
        });
        try {
            renderFiles(await api.get('/api/file/page?' + params.toString()));
        } catch (err) {
            $('#file-tbody').innerHTML = '<tr><td class="empty" colspan="7">' + escapeHtml(err.message) + '</td></tr>';
            toast(err.message, true);
        }
    }

    function bindFiles() {
        $('#form-upload').addEventListener('submit', async (e) => {
            e.preventDefault();
            const form = e.target;
            const file = form.elements.file.files && form.elements.file.files[0];
            if (!file) {
                toast('请选择要上传的文件', true);
                return;
            }
            const params = new URLSearchParams({ bizType: form.elements.bizType.value });
            const bizId = form.elements.bizId.value.trim();
            if (bizId) {
                params.set('bizId', bizId);
            }
            const formData = new FormData();
            formData.append('file', file);
            try {
                const vo = await api.upload('/api/file/upload?' + params.toString(), formData);
                toast('上传成功：' + vo.originalName);
                form.reset();
                fileState.pageNum = 1;
                loadFiles();
            } catch (err) {
                toast(err.message, true);
            }
        });

        $('#btn-refresh-files').addEventListener('click', loadFiles);

        $('#file-prev').addEventListener('click', () => {
            if (fileState.pageNum > 1) {
                fileState.pageNum -= 1;
                loadFiles();
            }
        });

        $('#file-next').addEventListener('click', () => {
            fileState.pageNum += 1;
            loadFiles();
        });

        $('#file-tbody').addEventListener('click', async (e) => {
            const btn = e.target.closest('button[data-action]');
            if (!btn || btn.disabled) {
                return;
            }
            const id = encodeURIComponent(btn.dataset.id);
            const action = btn.dataset.action;
            if (action === 'preview') {
                window.open('/api/file/' + id + '/preview', '_blank');
                return;
            }
            if (action === 'download') {
                const link = document.createElement('a');
                link.href = '/api/file/' + id + '/download';
                document.body.appendChild(link);
                link.click();
                link.remove();
                return;
            }
            if (action === 'delete') {
                if (!window.confirm('确认删除该文件？将写入删除时间戳（逻辑删除）。')) {
                    return;
                }
                try {
                    await api.del('/api/file/' + id);
                    toast('已删除');
                    loadFiles();
                } catch (err) {
                    toast(err.message, true);
                }
            }
        });
    }

    /* ==================== 初始化 ==================== */

    document.addEventListener('DOMContentLoaded', () => {
        if (window.location.protocol === 'file:') {
            $('#protocol-warn').hidden = false;
        }
        bindTabs();
        bindMine();
        bindUsers();
        bindFiles();
        loadMe(true);
    });
})();
