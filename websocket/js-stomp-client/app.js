import { Client } from "@stomp/stompjs";

import { WebSocket } from "ws";
Object.assign(global, { WebSocket });

import {
  postBoard,
  postMember,
  postVote,
  postVoteItem,
  postVoteLog,
  getRandomInteger,
} from "./http-test.js";

// const serverHost = ""; // Personal EC2
// const serverHost = ""; // Sinor EC2
const serverHost = "localhost";

const connectionHeaders = {
  login: "server",
  passcode: "verysecret",
};

// 투표 기록에 대한 생성 및 삭제 요청 시
// 메시지 Body(Payload)로 사용되는 DTO
class voteLogDto {
  id;
  voteItemId;
  memberId;
  constructor(
    voteLogId = getRandomInteger(),
    voteItemId = getRandomInteger(),
    memberId = getRandomInteger()
  ) {
    this.id = voteLogId;
    this.voteItemId = voteItemId;
    this.memberId = memberId;
  }
}

let memberId, boardId, voteId, voteItemId, voteLogId;
let didSavedLog, willUpdateLog;

Promise.resolve(postMember())
  .then((res) => {
    memberId = res.id;
    console.log(`memberId = ${memberId}`);
  })
  .then((res) => postBoard())
  .then((res) => {
    boardId = res.id;
    console.log(`boardId = ${boardId}`);
  })
  .then((res) => postVote(boardId))
  .then((res) => {
    voteId = res.id;
    console.log(`voteId = ${voteId}`);
  })
  .then((res) => postVoteItem(voteId))
  .then((res) => {
    voteItemId = res.id;
    console.log(`voteItemId = ${voteItemId}`);
  })
  .then((res) => postVoteLog(voteItemId, memberId))
  .then((res) => {
    voteLogId = res.id;
    console.log(`voteLogId = ${voteLogId}`);
  })
  .then((res) => {
    didSavedLog = new voteLogDto(voteLogId, voteItemId, memberId);
    willUpdateLog = new voteLogDto(voteLogId, voteItemId - 1, memberId);
    // console.log(didSavedLog);
    // console.log(willUpdateLog);
  })
  .then((res) => {
    let status = 0;
    const client = new Client({
      brokerURL: `ws://${serverHost}:15674/ws`,
      connectHeaders: connectionHeaders,
      onConnect: () => {
        client.subscribe(`/exchange/vote.client/${voteId}.#`, (message) => {
          console.log(`header: ${message.headers["method"]}`);
          console.log(`body: ${message.body}`);
          status = status + 1;
        });

        if (status === 0) {
          client.publish({
            headers: { method: "delete" },
            destination: `/exchange/vote.server/${voteId}`,
            body: JSON.stringify(didSavedLog),
          });
          client.publish({
            headers: { method: "post" },
            destination: `/exchange/vote.server/${voteId}`,
            body: JSON.stringify(willUpdateLog),
          });
        }
      },
    });
    client.activate();
  });
