import {findByName, ROOT_NAME} from 'mokapi/faker';
import {on} from 'mokapi';

const myUserId = '7c3a40a8-3f57-4645-8cf5-820a6a8d3613';
const otherUserIds = [
    '8d4b51b9-4a68-5756-9da6-931b7b9e4724',
    'f3ce1a6b-aba1-478d-9f01-ec484f23ce60', // Recycled generic ID
    '99999999-1234-5678-90ab-cdef12345678'
];
const allUserIds = [myUserId, ...otherUserIds];

function getRandomUserId() {
    return allUserIds[Math.floor(Math.random() * allUserIds.length)];
}

function getRandomOtherUserId() {
    return otherUserIds[Math.floor(Math.random() * otherUserIds.length)];
}

export default function() {
    // 1. Faker customization for generic data
    const root = findByName(ROOT_NAME);
    root.children.unshift({
        name: 'User IDs in Tasks/Expenses',
        attributes: ['assignedTo', 'paidBy', 'creditorId', 'debtorId', 'memberId'],
        fake: (r) => {
            let random = Math.random();
            let userId = getRandomOtherUserId();
            console.log(`Faking a user id randomly. Random is ${random}, returning ${userId}`);
            return userId;
        }
    })

    // Expense: splitBetween
    root.children.unshift({
        name: 'Expense Splits',
        attributes: ['splitBetween'],
        fake: () => {
            console.log(`Faking a splits`);
            const count = Math.floor(Math.random() * 3) + 1;
            const selected = new Set();
            if (Math.random() < 0.5) selected.add(myUserId);
            while(selected.size < count) {
                selected.add(getRandomUserId());
            }
            return Array.from(selected);
        }
    });


    // 2. HTTP Interception for Schema consistency
    on('http', function(request, response) {
        // Intercept Member generation
        // Check identifying features of the request (e.g. OperationID or Path)
        // We can inspect request.operationId if available, or regex the path
        

        // Example: List Members
        if (request.operationId === 'getMembers') {
             // We can generate a consistent list here
             const list = [];
             // Add me
             list.push({
                id: myUserId,
                name: 'It is Me',
                email: 'me@household.app',
                avatar: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
             });
             // Add others
             for (const uid of otherUserIds) {
                 list.push({
                     id: uid,
                     name: 'Member ' + uid.substring(0, 5),
                     email: 'user-' + uid.substring(0, 5) + '@household.app',
                     avatar: ''
                 });
             }
             response.data = list;
             return true;
        }
        
        // Example: Get Member
        if (request.operationId === 'getMember') {
            // Check path param? 
            // request.path['memberId'] ??? Need to check API
            const segments = request.url.path.split('/');
            const memberId = segments[segments.length - 1]; // rough guess

            if (memberId === myUserId) {
                 response.data = {
                    id: myUserId,
                    name: 'It is Me',
                    email: 'me@household.app',
                    avatar: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='
                };
            } else {
                 response.data = {
                    id: memberId,
                    name: 'Member ' + memberId.substring(0, 5),
                    email: 'user-' + memberId.substring(0, 5) + '@household.app',
                    avatar: ''
                };
            }
        }

        // Example: Create Member
        if (request.operationId === 'createMember') {
            // Ensure the created member always has myUserId
            const newMember = request.body || {};
            newMember.id = myUserId;
            response.data = newMember;
        }

        // Example: List Tasks (Manual override to ensure distribution)
        if (request.operationId === 'getTasks') {
             const tasks = [];
             const verbs = ['Clean', 'Buy', 'Fix', 'Wash', 'Pay'];
             const nouns = ['Dishes', 'Groceries', 'Lamp', 'Car', 'Bills', 'Windows', 'Floor'];
             
             for (let i = 0; i < 15; i++) {
                 const isAssignedToMe = Math.random() < 0.4; // 40% me
                 let assignedTo = undefined;
                 
                 if (isAssignedToMe) {
                     assignedTo = myUserId;
                 } else if (Math.random() < 0.8) { // Remaining 60%: 0.8 * 0.6 = 48% others, 12% unassigned
                     assignedTo = getRandomOtherUserId();
                 }

                 // Generate generic UUID like string for ID
                 const id = '00000000-0000-0000-0000-' + (100000000000 + i).toString();
                 const isRecurring = i % 3 === 0;
                 const isDone = i % 4 === 0;

                 const task = {
                     id: id,
                     title: `${verbs[i % verbs.length]} ${nouns[i % nouns.length]}`,
                     dueDate: new Date(Date.now() + (i - 5) * 86400000).toISOString().split('T')[0],
                     description: 'Auto-generated mock task for testing.',
                     recurring: isRecurring
                 };
                 
                 if (assignedTo) {
                     task.assignedTo = assignedTo;
                 }

                 if (isRecurring) {
                     task.recurrenceUnit = 'weeks';
                     task.recurrenceInterval = 1;
                 }
                 
                 if (isDone) {
                     task.done = new Date(Date.now() - 86400000).toISOString().split('T')[0];
                 }

                 tasks.push(task);
             }
             response.data = tasks;
             return true;
        }

    });

};

// Reload trigger 12350
